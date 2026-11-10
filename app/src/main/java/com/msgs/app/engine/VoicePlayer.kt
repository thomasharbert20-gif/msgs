package com.msgs.app.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Plays one voice message at a time and reports its progress. */
object VoicePlayer {
    data class State(
        val key: String? = null,
        val playing: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    /** Play [file], or pause/resume it if it's the one already loaded. */
    fun toggle(key: String, file: File) {
        val current = player
        if (_state.value.key == key && current != null) {
            if (current.isPlaying) {
                current.pause()
                ticker?.cancel()
                _state.update { it.copy(playing = false) }
            } else {
                current.start()
                _state.update { it.copy(playing = true) }
                startTicker()
            }
            return
        }
        stop()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.path)
            mp.setOnCompletionListener { stop() }
            mp.prepare()
            mp.start()
            player = mp
            _state.value = State(key = key, playing = true, positionMs = 0, durationMs = mp.duration)
            startTicker()
        } catch (e: Exception) {
            stop()
            Engine.toast("Couldn't play that voice message")
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = main.launch {
            while (isActive) {
                val p = player ?: break
                val pos = runCatching { p.currentPosition }.getOrDefault(0)
                _state.update { it.copy(positionMs = pos) }
                delay(50)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        _state.value = State()
    }
}

/** Length of an audio file in milliseconds, or 0 if it can't be read. */
fun audioDurationMs(file: File): Int = runCatching {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(file.path)
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
    } finally {
        r.release()
    }
}.getOrDefault(0)

/** Records a voice message to an .m4a file in the media cache. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean {
        cancel()
        val dir = File(context.cacheDir, "media").apply { mkdirs() }
        val f = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(f.path)
            r.prepare()
            r.start()
            recorder = r
            file = f
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            f.delete()
            false
        }
    }

    /** Stops recording and returns the file, or null when it was too short to keep. */
    fun finish(): File? {
        val r = recorder ?: return null
        val f = file
        val longEnough = System.currentTimeMillis() - startedAt >= 800
        val stopped = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        file = null
        return if (stopped && longEnough && f != null) {
            f
        } else {
            f?.delete()
            null
        }
    }

    fun cancel() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        file?.delete()
        file = null
    }
}
