package com.msgs.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.msgs.app.BuildConfig
import com.msgs.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the GitHub repo's latest release for a newer build, downloads its APK
 * and hands it to Android's installer. A token is only needed for a private repo.
 */
object Updater {
    data class Release(val build: Int, val title: String, val notes: String, val apkUrl: String, val assetApiUrl: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val build: Int) : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release, val file: File) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val API = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    fun setFailed(message: String) {
        _state.value = State.Failed(message)
    }

    /** Checks at most every 6 hours unless forced. */
    suspend fun check(context: Context, force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        if (!com.msgs.app.BuildConfig.UPDATER_ENABLED) return@withContext null
        if (!force && System.currentTimeMillis() - Prefs.lastUpdateCheck < 6 * 3600_000L) {
            return@withContext (_state.value as? State.Available)?.release
        }
        _state.value = State.Checking
        try {
            val token = TokenStore.get(context)
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Msgs-Android")
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            val code = conn.responseCode
            when {
                code == 404 && token.isNullOrBlank() -> throw IOException("No builds found. If the repo is private, add a GitHub token below.")
                code == 404 -> throw IOException("No builds found, or the token can't see the repo.")
                code == 401 || code == 403 -> throw IOException("GitHub refused the request. Check the token.")
                code !in 200..299 -> throw IOException("GitHub returned an error ($code). Try again later.")
            }
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name")
            val build = Regex("\\d+").findAll(tag).lastOrNull()?.value?.toIntOrNull() ?: 0
            val assets = o.optJSONArray("assets") ?: JSONArray()
            var apk: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    apk = a
                    break
                }
            }
            if (apk == null) throw IOException("The newest build has no app file attached yet.")
            val release = Release(
                build = build,
                title = o.optString("name").ifBlank { tag },
                notes = o.optString("body"),
                apkUrl = apk.optString("browser_download_url"),
                assetApiUrl = apk.optString("url"),
                size = apk.optLong("size"),
            )
            Prefs.lastUpdateCheck = System.currentTimeMillis()
            _state.value = if (build > BuildConfig.VERSION_CODE) State.Available(release) else State.UpToDate(BuildConfig.VERSION_CODE)
            release.takeIf { build > BuildConfig.VERSION_CODE }
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Couldn't check for updates")
            null
        }
    }

    suspend fun download(context: Context, release: Release): File? = withContext(Dispatchers.IO) {
        try {
            _state.value = State.Downloading(release, 0f)
            val token = TokenStore.get(context)
            val url = if (!token.isNullOrBlank()) signedAssetUrl(release.assetApiUrl, token) else release.apkUrl
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "msgs-${release.build}.apk")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Msgs-Android")
                connectTimeout = 20_000
                readTimeout = 60_000
            }
            if (conn.responseCode !in 200..299) throw IOException("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastEmit > 256 * 1024) {
                            lastEmit = done
                            _state.value = State.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            _state.value = State.Ready(release, out)
            out
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Download failed")
            null
        }
    }

    /** For private repos: ask the API for the asset, which redirects to a short-lived signed link. */
    private fun signedAssetUrl(apiUrl: String, token: String): String {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "Msgs-Android")
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        val code = conn.responseCode
        if (code in 300..399) return conn.getHeaderField("Location") ?: throw IOException("GitHub didn't return a download link")
        throw IOException("Couldn't get the download link ($code)")
    }

    /** True if Android lets Msgs install updates; otherwise opens the setting to allow it. */
    fun ensureInstallPermission(context: Context): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return false
    }

    fun install(context: Context, file: File) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, InstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Install failed")
        }
    }
}
