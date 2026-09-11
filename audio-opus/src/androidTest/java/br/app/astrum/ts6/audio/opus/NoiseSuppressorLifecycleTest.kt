package br.app.astrum.ts6.audio.opus

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoiseSuppressorLifecycleTest {
    @Test
    fun optionalNoiseSuppressorCanBeReleasedAroundRecorderLifecycle() {
        if (!runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false)) return

        val record = runCatching {
            val minimumBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minimumBuffer <= 0) return
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minimumBuffer)
                .build()
        }.getOrNull() ?: return

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }

        val suppressor = runCatching { NoiseSuppressor.create(record.audioSessionId) }.getOrNull()
        if (suppressor == null) {
            record.release()
            return
        }

        try {
            if (suppressor.setEnabled(true) != AudioEffect.SUCCESS) return
            runCatching { record.startRecording() }
            runCatching { record.stop() }
        } finally {
            runCatching { suppressor.setEnabled(false) }
            suppressor.release()
            record.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
