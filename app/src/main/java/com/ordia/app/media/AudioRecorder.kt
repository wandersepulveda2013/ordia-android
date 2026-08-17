package com.ordia.app.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Grabación y metadatos de audio en almacenamiento privado (filesDir/notes-media).
 *
 * Usa MediaRecorder (m4a/AAC). 100% local; no se transmite nada.
 * El reproductor se maneja en [AudioPlayer].
 */
object AudioRecorder {
    private const val TAG = "AudioRecorder"
    private const val DIR = "notes-media"

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Inicia la grabación. Devuelve la ruta donde se escribirá. */
    fun start(context: Context): String? {
        if (recorder != null) return outputFile?.absolutePath
        return runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "aud-${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            outputFile = file
            file.absolutePath
        }.onFailure { Log.w(TAG, "start failed", it) }.getOrNull()
    }

    /** Detiene la grabación. Devuelve la ruta del archivo o null si falló. */
    fun stop(): String? {
        val r = recorder ?: return null
        val file = outputFile
        return runCatching {
            r.stop()
            r.release()
            file?.absolutePath
        }.onFailure { Log.w(TAG, "stop failed", it) }.getOrNull().also {
            recorder = null
            outputFile = null
        }
    }

    /** Cancela la grabación y borra el parcial. */
    fun cancel() {
        val r = recorder
        recorder = null
        outputFile = null
        runCatching {
            r?.stop(); r?.release()
        }.onFailure { Log.w(TAG, "cancel stop", it) }
        outputFile?.delete()
    }

    /** Duración en ms de un archivo de audio (o -1 si no se pudo leer). */
    fun durationMs(path: String): Long {
        val file = File(path)
        if (!file.exists()) return -1L
        return runCatching {
            MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(path)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
            }
        }.getOrDefault(-1L)
    }
}

/**
 * Reproductor de audio ligero para bloques AUDIO.
 *
 * Expone estado simple (isPlaying, positionMs, durationMs, speed) vía callbacks.
 * El composable que lo usa sondea position con un LaunchedEffect.
 */
class AudioPlayer {
    private var player: MediaPlayer? = null
    private var source: String? = null

    val isPlaying: Boolean get() = player?.isPlaying == true
    var speed: Float = 1f
        private set

    fun load(path: String, onReady: (Long) -> Unit = {}) {
        release()
        source = path
        player = MediaPlayer().apply {
            try {
                setDataSource(path)
                prepare()
                setPlaybackParams(playbackParams.setSpeed(speed))
                onReady(duration.toLong())
            } catch (e: Exception) {
                Log.w("AudioPlayer", "load failed", e)
                release()
            }
        }
    }

    fun play() {
        player?.let {
            if (!it.isPlaying) {
                runCatching {
                    it.setPlaybackParams(it.playbackParams.setSpeed(speed))
                    it.start()
                }
            }
        }
    }

    fun pause() {
        runCatching { player?.pause() }
    }

    fun seekTo(ms: Int) {
        runCatching { player?.seekTo(ms) }
    }

    fun setSpeed(s: Float) {
        speed = s
        runCatching {
            player?.setPlaybackParams(player!!.playbackParams.setSpeed(s))
        }
    }

    fun positionMs(): Int = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
    fun durationMs(): Int = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    fun release() {
        runCatching { player?.release() }
        player = null
    }
}

object AudioFormat {
    /** Formatea milisegundos como m:ss (o h:mm:ss si >1h). */
    fun format(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
