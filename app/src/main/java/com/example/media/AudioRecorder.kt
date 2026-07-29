package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    companion object {
        private const val TAG = "AudioRecorder"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording(): File? {
        val outputFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.m4a")
        currentFile = outputFile

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            stopRecording()
            return null
        }
    }

    fun stopRecording(): File? {
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            currentFile
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            null
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            currentFile?.delete()
            currentFile = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling audio recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            currentFile?.delete()
            currentFile = null
        }
    }
}
