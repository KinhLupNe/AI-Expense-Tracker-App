package com.example.aiexpensetrackeropenai.ui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    var audioFile: File? = null
        private set

    fun startRecording() {
        val cacheDir = context.cacheDir
        audioFile = File(cacheDir, "audio_input.m4a")

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setMaxDuration(10000) // 10s max duration limit
            setMaxFileSize(1000000) // 1MB max file size limit
            setOutputFile(audioFile?.absolutePath)
            
            prepare()
            start()
        }
    }

    fun stopRecording() {
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // Ignore stop errors if stopped too quickly
        } finally {
            recorder?.release()
            recorder = null
        }
    }

    fun destroy() {
        try {
            recorder?.release()
            recorder = null
        } catch (e: Exception) {}
    }

    fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
