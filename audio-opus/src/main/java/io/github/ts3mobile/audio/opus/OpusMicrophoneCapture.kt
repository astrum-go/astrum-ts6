package io.github.ts3mobile.audio.opus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioDeviceInfo
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import io.github.ts3mobile.protocol.EncodedVoiceSource
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.sqrt

class OpusMicrophoneCapture(
    context: Context,
    private val onFailure: (Throwable) -> Unit = {},
) : EncodedVoiceSource, AutoCloseable {
    private val applicationContext = context.applicationContext
    private val encodedFrames = ArrayBlockingQueue<ByteArray>(ENCODED_QUEUE_CAPACITY)
    private val lifecycleLock = Any()
    private val encodedFrameCount = AtomicLong(0L)
    private val providedFrameCount = AtomicLong(0L)
    private val capturedNonZeroPcm = AtomicBoolean(false)
    private val suppressionMode = AtomicReference(SuppressionMode.RNNOISE)

    @Volatile
    private var control: CaptureControl? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    private var activeNoiseSuppressor: NoiseSuppressor? = null

    @Volatile
    private var preferredDevice: AudioDeviceInfo? = null

    @Volatile
    private var worker: Thread? = null

    @Volatile
    var systemAudioCapture: SystemAudioCapture? = null

    @Volatile
    var isVoiceTransmitting: Boolean = true

    val isCapturing: Boolean
        get() = control?.running?.get() == true

    fun setSuppressionMode(mode: SuppressionMode) {
        suppressionMode.set(mode)
        activeNoiseSuppressor?.let { suppressor ->
            val enable = mode == SuppressionMode.BOTH || mode == SuppressionMode.NOISE_SUPPRESSOR
            if (!setEffectEnabled(suppressor, enable, "noise suppressor")) {
                if (!enable) activeNoiseSuppressor = null
            }
        }
    }

    fun setPreferredDevice(device: AudioDeviceInfo?) {
        preferredDevice = device
        audioRecord?.let { record ->
            runCatching { record.setPreferredDevice(device) }
                .onFailure { error ->
                    System.err.println("TS3_AUDIO: failed to route microphone: ${error.message}")
                }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        synchronized(lifecycleLock) {
            if (control?.running?.get() == true) return
            check(
                applicationContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED,
            ) { "Microphone permission is not granted" }

            val minimumBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minimumBytes > 0) { "Unable to determine microphone buffer size: $minimumBytes" }

            val encoder = NativeOpusEncoder()
            val denoiser = try {
                NativeRnNoiseProcessor()
            } catch (error: Throwable) {
                encoder.close()
                throw error
            }
            val deepFilter = try {
                NativeDeepFilterProcessor(applicationContext)
            } catch (error: Throwable) {
                System.err.println("TS3_AUDIO: failed to initialize DeepFilter: ${error.message}")
                null
            }
            val transientSuppressor = try {
                NativeTransientSuppressor(SAMPLE_RATE)
            } catch (error: Throwable) {
                System.err.println("TS3_AUDIO: failed to initialize TransientSuppressor: ${error.message}")
                null
            }
            val currentMode = suppressionMode.get()
            val audioSource = if (currentMode == SuppressionMode.NOISE_SUPPRESSOR) {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }
            val record = try {
                AudioRecord.Builder()
                    .setAudioSource(audioSource)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(max(minimumBytes, CAPTURE_BUFFER_BYTES))
                    .build()
            } catch (error: Throwable) {
                transientSuppressor?.close()
                deepFilter?.close()
                denoiser.close()
                encoder.close()
                throw error
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                transientSuppressor?.close()
                deepFilter?.close()
                denoiser.close()
                encoder.close()
                error("Unable to initialize microphone")
            }
            preferredDevice?.let { device ->
                if (!record.setPreferredDevice(device)) {
                    System.err.println("TS3_AUDIO: microphone device preference was rejected")
                }
            }
            val audioEffects = createAudioEffects(record.audioSessionId, suppressionMode.get())
            val sessionNoiseSuppressor = audioEffects.firstOrNull { it is NoiseSuppressor } as? NoiseSuppressor
            activeNoiseSuppressor = sessionNoiseSuppressor

            try {
                record.startRecording()
                check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Unable to start microphone"
                }
            } catch (error: Throwable) {
                runCatching { record.stop() }
                audioEffects.forEach { runCatching { it.release() } }
                activeNoiseSuppressor = null
                record.release()
                transientSuppressor?.close()
                deepFilter?.close()
                denoiser.close()
                encoder.close()
                throw error
            }

            encodedFrames.clear()
            encodedFrameCount.set(0L)
            providedFrameCount.set(0L)
            capturedNonZeroPcm.set(false)
            val newControl = CaptureControl()
            control = newControl
            audioRecord = record
            worker = Thread(
                { captureLoop(record, encoder, denoiser, deepFilter, transientSuppressor, audioEffects, newControl) },
                "ts3-opus-capture",
            ).apply { start() }
        }
    }

    fun stop() {
        val activeControl: CaptureControl
        val record: AudioRecord?
        val activeWorker: Thread?
        synchronized(lifecycleLock) {
            activeControl = control ?: run {
                encodedFrames.clear()
                return
            }
            activeControl.running.set(false)
            record = audioRecord
            activeWorker = worker
            encodedFrames.clear()
        }

        runCatching { record?.stop() }
        activeWorker?.interrupt()
        if (activeWorker !== Thread.currentThread()) {
            runCatching { activeWorker?.join(STOP_JOIN_TIMEOUT_MS) }
        }
        synchronized(lifecycleLock) {
            if (control === activeControl) {
                control = null
                worker = null
            }
        }
    }

    override fun isReady(): Boolean = encodedFrames.isNotEmpty()

    override fun pollEncodedFrame(): ByteArray? = encodedFrames.poll()?.also { frame ->
        val provided = providedFrameCount.incrementAndGet()
        if (provided == 1L) {
            System.err.println("TS3_AUDIO: provided first microphone Opus frame (${frame.size} bytes)")
        }
    }

    override fun close() = stop()

    private fun captureLoop(
        record: AudioRecord,
        encoder: NativeOpusEncoder,
        denoiser: NativeRnNoiseProcessor,
        deepFilter: NativeDeepFilterProcessor?,
        transientSuppressor: NativeTransientSuppressor?,
        audioEffects: List<AudioEffect>,
        captureControl: CaptureControl,
    ) {
        val denoiserPcm = ShortArray(RNNOISE_FRAME_SAMPLES)
        val opusPcm = ShortArray(OPUS_FRAME_SAMPLES)
        var denoiserOffset = 0
        var opusOffset = 0
        var denoisedFrameCount = 0L
        var totalDenoiseNanos = 0L
        var maxDenoiseNanos = 0L
        var vadTotal = 0.0
        var inputEnergy = 0.0
        var outputEnergy = 0.0
        var measuredSamples = 0L
        var lastSuppressionMode = suppressionMode.get()
        val voiceGate = VoiceGate()
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            while (captureControl.running.get()) {
                val read = record.read(
                    denoiserPcm,
                    denoiserOffset,
                    denoiserPcm.size - denoiserOffset,
                    AudioRecord.READ_BLOCKING,
                )
                if (!captureControl.running.get()) break
                if (read < 0) error("AudioRecord read failed: $read")
                if (read == 0) continue
                denoiserOffset += read
                if (denoiserOffset < denoiserPcm.size) continue

                val sysCap = systemAudioCapture
                if (sysCap != null) {
                    val sysSamples = ShortArray(denoiserPcm.size)
                    val sysRead = sysCap.readSamples(sysSamples, 0, sysSamples.size)
                    val voiceAllowed = isVoiceTransmitting
                    for (i in denoiserPcm.indices) {
                        val micVal = if (voiceAllowed) denoiserPcm[i].toInt() else 0
                        val sysVal = if (i < sysRead) sysSamples[i].toInt() else 0
                        denoiserPcm[i] = (micVal + sysVal).coerceIn(-32768, 32767).toShort()
                    }
                } else if (!isVoiceTransmitting) {
                    denoiserPcm.fill(0)
                }

                var rawNonZero = false
                denoiserPcm.forEach { sample ->
                    val value = sample.toDouble()
                    inputEnergy += value * value
                    if (sample.toInt() != 0) rawNonZero = true
                }
                if (!capturedNonZeroPcm.get() && rawNonZero) {
                    capturedNonZeroPcm.set(true)
                }
                val currentMode = suppressionMode.get()
                val isClarityMode = currentMode == SuppressionMode.ASTRUM_CLARITY
                val rnNoiseActive = currentMode == SuppressionMode.RNNOISE || currentMode == SuppressionMode.BOTH || isClarityMode
                val deepFilterActive = currentMode == SuppressionMode.DEEPFILTER || (isClarityMode && deepFilter?.isReady == true)
                val wasRnNoiseActive = lastSuppressionMode == SuppressionMode.RNNOISE ||
                    lastSuppressionMode == SuppressionMode.BOTH ||
                    lastSuppressionMode == SuppressionMode.ASTRUM_CLARITY
                if (rnNoiseActive != wasRnNoiseActive) {
                    if (!rnNoiseActive) voiceGate.reset()
                    lastSuppressionMode = currentMode
                }

                // Estágio 1: Supressão nativa de transientes C++ (Anti-teclado mecânico e cliques)
                val transientActive = isClarityMode || currentMode == SuppressionMode.DEEPFILTER || currentMode == SuppressionMode.RNNOISE
                val transientScore = if (transientActive && transientSuppressor != null) {
                    transientSuppressor.processInPlace(denoiserPcm)
                } else {
                    0f
                }

                // Estágio 2: Filtragem neural e VoiceGate adaptativo
                if (deepFilterActive && deepFilter != null && deepFilter.isReady) {
                    val denoiseStarted = System.nanoTime()
                    val metric = deepFilter.processInPlace(denoiserPcm)
                    val denoiseNanos = System.nanoTime() - denoiseStarted
                    totalDenoiseNanos += denoiseNanos
                    maxDenoiseNanos = max(maxDenoiseNanos, denoiseNanos)
                    vadTotal += metric.toDouble()
                    denoisedFrameCount++

                    if (isClarityMode || currentMode == SuppressionMode.DEEPFILTER) {
                        voiceGate.processInPlace(denoiserPcm, vadProbability = metric, transientScore = transientScore)
                    }
                } else if (rnNoiseActive) {
                    val denoiseStarted = System.nanoTime()
                    val vadProbability = denoiser.processInPlace(denoiserPcm)
                    val denoiseNanos = System.nanoTime() - denoiseStarted
                    totalDenoiseNanos += denoiseNanos
                    maxDenoiseNanos = max(maxDenoiseNanos, denoiseNanos)
                    vadTotal += vadProbability
                    denoisedFrameCount++
                    voiceGate.processInPlace(denoiserPcm, vadProbability, transientScore)
                }
                denoiserPcm.forEach { sample ->
                    val value = sample.toDouble()
                    outputEnergy += value * value
                }
                measuredSamples += denoiserPcm.size

                denoiserPcm.copyInto(opusPcm, destinationOffset = opusOffset)
                opusOffset += denoiserPcm.size
                denoiserOffset = 0
                if (opusOffset < opusPcm.size) continue

                val encoded = encoder.encode(opusPcm)
                if (!encodedFrames.offer(encoded)) {
                    encodedFrames.poll()
                    encodedFrames.offer(encoded)
                }
                val count = encodedFrameCount.incrementAndGet()
                if (count == 1L) {
                    System.err.println(
                        "TS3_AUDIO: encoded first microphone frame " +
                            "(${encoded.size} bytes, nonZero=${opusPcm.any { it.toInt() != 0 }})",
                    )
                }
                opusOffset = 0
            }
        } finally {
            if (measuredSamples > 0L) {
                val inputRms = sqrt(inputEnergy / measuredSamples)
                val outputRms = sqrt(outputEnergy / measuredSamples)
                val averageDenoiseMicros = if (denoisedFrameCount == 0L) {
                    0L
                } else {
                    totalDenoiseNanos / denoisedFrameCount / 1_000L
                }
                val encodedCount = encodedFrameCount.get()
                val averageVad = if (denoisedFrameCount == 0L) {
                    0.0
                } else {
                    vadTotal / denoisedFrameCount
                }
                System.err.println(
                    "TS3_AUDIO: microphone session ended " +
                        "(encoded=$encodedCount, provided=${providedFrameCount.get()}, " +
                        "nonZero=${capturedNonZeroPcm.get()}, " +
                        "denoiseAvg=${averageDenoiseMicros}us, " +
                        "denoiseMax=${maxDenoiseNanos / 1_000L}us, " +
                        "inputRms=$inputRms, outputRms=$outputRms, " +
                        "vad=${"%.3f".format(averageVad)})",
                )
            }
            runCatching { record.stop() }
            audioEffects.forEach { runCatching { it.release() } }
            activeNoiseSuppressor = null
            runCatching { record.release() }
            transientSuppressor?.close()
            deepFilter?.close()
            denoiser.close()
            encoder.close()
            synchronized(lifecycleLock) {
                if (control === captureControl) control = null
                if (audioRecord === record) audioRecord = null
                if (worker === Thread.currentThread()) worker = null
            }
        }
    }

    private class CaptureControl {
        val running = AtomicBoolean(true)
    }

    private fun createAudioEffects(audioSessionId: Int, mode: SuppressionMode): List<AudioEffect> = buildList {
        runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                createConfiguredEffect("acoustic echo canceler", enabled = true) {
                    AcousticEchoCanceler.create(audioSessionId)
                }?.let(::add)
            }
        }.onFailure { error ->
            System.err.println("TS3_AUDIO: acoustic echo canceler unavailable: ${error.message}")
        }
        if (mode == SuppressionMode.NOISE_SUPPRESSOR || mode == SuppressionMode.BOTH) {
            runCatching { createNoiseSuppressor(audioSessionId) }
                .onFailure { error ->
                    System.err.println("TS3_AUDIO: noise suppressor unavailable: ${error.message}")
                }
                .getOrNull()
                ?.let(::add)
        }
    }

    private fun createNoiseSuppressor(audioSessionId: Int): NoiseSuppressor? {
        val available = try {
            NoiseSuppressor.isAvailable()
        } catch (error: Throwable) {
            System.err.println("TS3_AUDIO: noise suppressor availability check failed: ${error.message}")
            return null
        }
        if (!available) return null

        return try {
            NoiseSuppressor.create(audioSessionId)
        } catch (error: Throwable) {
            System.err.println("TS3_AUDIO: noise suppressor unavailable: ${error.message}")
            return null
        }
    }

    private fun setEffectEnabled(effect: AudioEffect, enabled: Boolean, label: String): Boolean = try {
        val result = effect.setEnabled(enabled)
        if (result != AudioEffect.SUCCESS) {
            System.err.println("TS3_AUDIO: failed to set $label enabled=$enabled (status=$result)")
            false
        } else {
            System.err.println("TS3_AUDIO: ${if (enabled) "enabled" else "disabled"} $label")
            true
        }
    } catch (error: IllegalStateException) {
        System.err.println("TS3_AUDIO: failed to set $label enabled=$enabled: ${error.message}")
        false
    } catch (error: Throwable) {
        System.err.println("TS3_AUDIO: failed to set $label enabled=$enabled: ${error.message}")
        false
    }

    private fun <T : AudioEffect> createConfiguredEffect(
        label: String,
        enabled: Boolean,
        factory: () -> T?,
    ): T? {
        val effect = try {
            factory()
        } catch (error: Throwable) {
            System.err.println("TS3_AUDIO: $label unavailable: ${error.message}")
            return null
        } ?: return null

        return try {
            effect.enabled = enabled
            System.err.println("TS3_AUDIO: ${if (enabled) "enabled" else "disabled"} $label")
            effect
        } catch (error: Throwable) {
            runCatching { effect.release() }
            System.err.println("TS3_AUDIO: failed to configure $label: ${error.message}")
            null
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val RNNOISE_FRAME_SAMPLES = 480
        const val OPUS_FRAME_SAMPLES = 960
        const val FRAME_BYTES = OPUS_FRAME_SAMPLES * 2
        const val CAPTURE_BUFFER_BYTES = FRAME_BYTES * 2
        const val ENCODED_QUEUE_CAPACITY = 3
        const val STOP_JOIN_TIMEOUT_MS = 1_500L
    }
}
