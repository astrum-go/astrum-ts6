package io.github.ts3mobile.audio.opus

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class SystemAudioCapture(
    private val mediaProjection: MediaProjection,
) : AutoCloseable {
    private val isRunning = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null

    // Circular ring buffer for 48kHz mono PCM short samples
    private val ringBuffer = ShortArray(48000)
    private var writePos = 0
    private var readPos = 0
    private val bufferLock = Any()

    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.Q)
    fun start() {
        if (isRunning.getAndSet(true)) return

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val sampleRate = 48000
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )

            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSize, 16384))
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord for AudioPlaybackCapture")
                isRunning.set(false)
                return
            }

            audioRecord = record
            record.startRecording()

            val thread = Thread({
                val stereoPcm = ShortArray(960)
                while (isRunning.get()) {
                    val read = record.read(stereoPcm, 0, stereoPcm.size, AudioRecord.READ_BLOCKING)
                    if (read > 0) {
                        synchronized(bufferLock) {
                            for (i in 0 until read step 2) {
                                val left = stereoPcm[i].toInt()
                                val right = if (i + 1 < read) stereoPcm[i + 1].toInt() else left
                                val mono = ((left + right) / 2).coerceIn(-32768, 32767).toShort()
                                ringBuffer[writePos] = mono
                                writePos = (writePos + 1) % ringBuffer.size
                                if (writePos == readPos) {
                                    readPos = (readPos + 1) % ringBuffer.size
                                }
                            }
                        }
                    }
                }
            }, "SystemAudioCaptureThread")
            worker = thread
            thread.start()
            Log.i(TAG, "SystemAudioCapture started successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start SystemAudioCapture", e)
            isRunning.set(false)
        }
    }

    fun readSamples(dest: ShortArray, offset: Int, count: Int): Int {
        if (!isRunning.get()) return 0
        synchronized(bufferLock) {
            var available = writePos - readPos
            if (available < 0) available += ringBuffer.size
            val toRead = minOf(available, count)
            for (i in 0 until toRead) {
                dest[offset + i] = ringBuffer[readPos]
                readPos = (readPos + 1) % ringBuffer.size
            }
            return toRead
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        runCatching { audioRecord?.stop() }
        worker?.interrupt()
        runCatching { worker?.join(500) }
        worker = null
        audioRecord?.release()
        audioRecord = null
    }

    override fun close() = stop()

    companion object {
        private const val TAG = "SystemAudioCapture"
    }
}
