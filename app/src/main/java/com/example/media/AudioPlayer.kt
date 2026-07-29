package com.example.media

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AudioPlayerState(
    val playingMessageId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0
)

class AudioPlayer(private val context: Context) {
    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    fun playAudio(messageId: String, pathOrUri: String) {
        if (_playerState.value.playingMessageId == messageId && mediaPlayer != null) {
            if (mediaPlayer?.isPlaying == true) {
                pauseAudio()
            } else {
                resumeAudio()
            }
            return
        }

        stopAudio()

        try {
            val player = MediaPlayer().apply {
                val file = File(pathOrUri)
                if (file.exists()) {
                    setDataSource(file.absolutePath)
                } else {
                    setDataSource(context, Uri.parse(pathOrUri))
                }
                prepare()
                start()
            }

            mediaPlayer = player

            _playerState.value = AudioPlayerState(
                playingMessageId = messageId,
                isPlaying = true,
                currentPositionMs = 0,
                durationMs = player.duration
            )

            player.setOnCompletionListener {
                stopAudio()
            }

            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file: $pathOrUri", e)
            stopAudio()
        }
    }

    fun pauseAudio() {
        try {
            mediaPlayer?.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
            progressJob?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing audio", e)
        }
    }

    fun resumeAudio() {
        try {
            mediaPlayer?.start()
            _playerState.value = _playerState.value.copy(isPlaying = true)
            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming audio", e)
        }
    }

    fun stopAudio() {
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio player", e)
        } finally {
            mediaPlayer = null
            _playerState.value = AudioPlayerState()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer ?: break
                if (player.isPlaying) {
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = player.currentPosition,
                        durationMs = player.duration
                    )
                }
                delay(100L)
            }
        }
    }
}
