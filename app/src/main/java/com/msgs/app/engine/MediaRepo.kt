package com.msgs.app.engine

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Downloads, decrypts and caches photos and attachments. */
object MediaRepo {
    private lateinit var app: Context
    private val thumbs = LruCache<String, ImageBitmap>(80)
    private val fulls = LruCache<String, ImageBitmap>(6)

    fun init(context: Context) {
        app = context.applicationContext
        File(app.cacheDir, "media").mkdirs()
    }

    private fun file(name: String) = File(app.cacheDir, "media/$name")

    suspend fun thumbnail(m: Media): ImageBitmap? = withContext(Dispatchers.IO) {
        val id = m.thumbId.ifBlank { m.mediaId }
        val key = m.thumbKey.takeIf { m.thumbId.isNotBlank() } ?: m.key
        if (id.isBlank()) return@withContext null
        thumbs.get(id)?.let { return@withContext it }
        val f = file("t_" + AvatarRepo.sha1(id))
        runCatching {
            if (!f.exists()) f.writeBytes(Engine.bridge.downloadMedia(id, key))
            decode(f, 900)
        }.getOrNull()?.also { thumbs.put(id, it) }
    }

    suspend fun fullImage(m: Media): ImageBitmap? = withContext(Dispatchers.IO) {
        fulls.get(m.mediaId)?.let { return@withContext it }
        val f = fullFile(m) ?: return@withContext null
        decode(f, 2400)?.also { fulls.put(m.mediaId, it) }
    }

    suspend fun fullFile(m: Media): File? = withContext(Dispatchers.IO) {
        if (m.mediaId.isBlank()) return@withContext null
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(m.mime) ?: "bin"
        val f = file("f_" + AvatarRepo.sha1(m.mediaId) + "." + ext)
        if (f.exists() && f.length() > 0) return@withContext f
        runCatching { Engine.bridge.downloadMediaToFile(m.mediaId, m.key, f.path) }
            .onFailure { Engine.report(it) }
        f.takeIf { it.exists() && it.length() > 0 }
    }

    /** Opens an attachment (video, audio, document) in another app. */
    suspend fun open(context: Context, m: Media) {
        val f = fullFile(m) ?: return
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, m.mime.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        withContext(Dispatchers.Main) {
            runCatching { context.startActivity(intent) }.onFailure { Engine.toast("No app on this phone can open that file") }
        }
    }

    private fun decode(f: File, maxSide: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = AvatarRepo.sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        return BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
    }
}
