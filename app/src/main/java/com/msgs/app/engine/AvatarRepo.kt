package com.msgs.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Contact and group pictures. Order of preference: a photo you picked in Msgs,
 * then the contact photo Google Messages has, then the group icon.
 */
object AvatarRepo {
    data class Key(val custom: String, val participantId: String?, val groupUrl: String?)

    private lateinit var app: Context
    private val mem = LruCache<String, ImageBitmap>(250)
    private val missing = HashSet<String>()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun init(context: Context) {
        app = context.applicationContext
        File(app.filesDir, "avatars").mkdirs()
        File(app.cacheDir, "avatars").mkdirs()
    }

    fun keyFor(conv: Conversation?, person: Participant? = null): Key {
        val p = person ?: conv?.other
        return if (p != null) {
            val id = p.rawNumber.ifBlank { p.number }.filter { it.isDigit() || it == '+' }.ifBlank { p.id }
            Key("p_" + sha1(id), p.id.ifBlank { null }, null)
        } else {
            Key("c_" + sha1(conv?.id ?: "none"), null, conv?.groupAvatarUrl?.ifBlank { null })
        }
    }

    private fun customFile(key: Key) = File(app.filesDir, "avatars/${key.custom}.jpg")
    private fun googleFile(participantId: String) = File(app.cacheDir, "avatars/g_${sha1(participantId)}")
    private fun groupFile(url: String) = File(app.cacheDir, "avatars/u_${sha1(url)}")

    fun hasCustom(key: Key) = customFile(key).exists()

    suspend fun load(key: Key): ImageBitmap? = withContext(Dispatchers.IO) {
        val memKey = key.toString()
        mem.get(memKey)?.let { return@withContext it }
        val candidates = listOfNotNull(
            customFile(key),
            key.participantId?.let { googleFile(it) },
            key.groupUrl?.let { groupFile(it) },
        )
        var bmp = candidates.firstOrNull { it.exists() && it.length() > 0 }?.let { decode(it, 320) }
        if (bmp == null && key.groupUrl != null && memKey !in missing) {
            bmp = runCatching {
                val bytes = Engine.bridge.downloadGroupAvatar(key.groupUrl)
                groupFile(key.groupUrl).writeBytes(bytes)
                decode(groupFile(key.groupUrl), 320)
            }.getOrNull()
            if (bmp == null) missing.add(memKey)
        }
        bmp?.also { mem.put(memKey, it) }
    }

    /** Fetch Google Messages' contact photos for these chats in a few batched calls. */
    suspend fun prefetch(convs: List<Conversation>) = withContext(Dispatchers.IO) {
        val week = 7L * 24 * 3600 * 1000
        val ids = convs.flatMap { it.others }.map { it.id }.filter { it.isNotBlank() }.distinct().filter { id ->
            val f = googleFile(id)
            !f.exists() || System.currentTimeMillis() - f.lastModified() > week
        }
        if (ids.isEmpty()) return@withContext
        var changed = false
        ids.chunked(20).forEach { chunk ->
            runCatching {
                val o = JSONObject(Engine.bridge.getAvatars(JSONArray(chunk).toString()))
                for (id in chunk) {
                    val b64 = o.optString(id)
                    val f = googleFile(id)
                    if (b64.isNotEmpty()) {
                        f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                        changed = true
                    } else if (!f.exists()) {
                        f.writeBytes(ByteArray(0))
                    } else {
                        f.setLastModified(System.currentTimeMillis())
                    }
                }
            }
        }
        if (changed) {
            mem.evictAll()
            _version.value++
        }
    }

    /** Save a photo you picked as this contact's or group's picture. */
    suspend fun setCustom(context: Context, key: Key, uri: Uri) = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, 640)
        val src = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Couldn't open that photo")
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = if (side > 640) Bitmap.createScaledBitmap(square, 640, 640, true) else square
        customFile(key).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        mem.evictAll()
        _version.value++
    }

    fun removeCustom(key: Key) {
        customFile(key).delete()
        mem.evictAll()
        _version.value++
    }

    private fun decode(file: File, maxSide: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        return BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
    }

    fun sampleSize(w: Int, h: Int, maxSide: Int): Int {
        var s = 1
        while (w / (s * 2) >= maxSide && h / (s * 2) >= maxSide) s *= 2
        return s
    }

    fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
