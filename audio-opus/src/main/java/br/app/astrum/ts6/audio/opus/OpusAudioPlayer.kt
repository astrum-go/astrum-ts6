package br.app.astrum.ts6.audio.opus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import br.app.astrum.ts6.protocol.VoiceFrame
import java.util.ArrayDeque
import java.util.TreeMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

class OpusAudioPlayer(context: Context) : AutoCloseable {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val inputQueue = ArrayBlockingQueue<QueuedFrame>(INPUT_QUEUE_CAPACITY)
    private val discontinuityClients = ConcurrentHashMap.newKeySet<Int>()
    private val participantGains = ConcurrentHashMap<Int, Float>()
    private val resetRequested = AtomicBoolean(false)
    private val userMuted = AtomicBoolean(false)
    private val focusAllowsPlayback = AtomicBoolean(false)
    private val receivedFrameCount = AtomicLong(0L)
    private val decodedPacketCount = AtomicLong(0L)
    private val loggedFirstFrame = AtomicBoolean(false)
    private val loggedFirstPcm = AtomicBoolean(false)
    private val lifecycleLock = Any()

    @Volatile
    private var control: PlaybackControl? = null

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var preferredDevice: AudioDeviceInfo? = null

    @Volatile
    private var worker: Thread? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val hasFocus = change == AudioManager.AUDIOFOCUS_GAIN
        focusAllowsPlayback.set(hasFocus)
        if (!hasFocus) resetRequested.set(true)
        applyVolume()
    }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes())
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    fun start() {
        synchronized(lifecycleLock) {
            if (control?.running?.get() == true) return

            val track = createAudioTrack()
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Unable to initialize audio output" }

            inputQueue.clear()
            discontinuityClients.clear()
            resetRequested.set(false)
            focusAllowsPlayback.set(false)
            receivedFrameCount.set(0L)
            decodedPacketCount.set(0L)
            loggedFirstFrame.set(false)
            loggedFirstPcm.set(false)

            val newControl = PlaybackControl()
            control = newControl
            audioTrack = track
            worker = Thread({ playbackLoop(track, newControl) }, "ts3-opus-playback").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        }
    }

    fun submit(frame: VoiceFrame) {
        val activeControl = control ?: return
        if (!activeControl.running.get() || userMuted.get()) return
        if ((participantGains[frame.clientId] ?: DEFAULT_PARTICIPANT_GAIN) <= 0f) return

        val queued = QueuedFrame(frame, System.nanoTime())
        receivedFrameCount.incrementAndGet()
        if (!inputQueue.offer(queued)) {
            inputQueue.poll()?.let { dropped ->
                discontinuityClients += dropped.frame.clientId
            }
            inputQueue.offer(queued)
        }
    }

    fun setMuted(muted: Boolean) {
        userMuted.set(muted)
        resetRequested.set(true)
        if (muted) inputQueue.clear()
        applyVolume()
    }

    fun replaceParticipantGains(gains: Map<Int, Float>) {
        val normalized = gains.mapValues { (_, gain) ->
            gain.coerceIn(MIN_PARTICIPANT_GAIN, MAX_PARTICIPANT_GAIN)
        }.filterValues { it != DEFAULT_PARTICIPANT_GAIN }
        if (participantGains == normalized) return

        val previous = participantGains.toMap()
        participantGains.clear()
        participantGains.putAll(normalized)

        (previous.keys + normalized.keys).forEach { clientId ->
            val oldGain = previous[clientId] ?: DEFAULT_PARTICIPANT_GAIN
            val newGain = normalized[clientId] ?: DEFAULT_PARTICIPANT_GAIN
            if ((oldGain <= 0f) != (newGain <= 0f)) discontinuityClients += clientId
        }
        inputQueue.removeIf { queued ->
            (normalized[queued.frame.clientId] ?: DEFAULT_PARTICIPANT_GAIN) <= 0f
        }
    }

    fun setPreferredDevice(device: AudioDeviceInfo?) {
        preferredDevice = device
        audioTrack?.let { track ->
            runCatching { track.setPreferredDevice(device) }
                .onFailure { error ->
                    System.err.println("TS3_AUDIO: failed to route playback: ${error.message}")
                }
        }
    }

    fun stop() {
        val activeControl: PlaybackControl
        val activeWorker: Thread?
        val track: AudioTrack?
        synchronized(lifecycleLock) {
            activeControl = control ?: return
            activeControl.running.set(false)
            activeWorker = worker
            track = audioTrack
            inputQueue.clear()
        }

        runCatching { track?.pause() }
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

    override fun close() = stop()

    private fun playbackLoop(track: AudioTrack, playbackControl: PlaybackControl) {
        val talkers = mutableMapOf<Int, TalkerState>()
        var outputStarted = false
        var nextTickNanos = 0L
        try {
            while (playbackControl.running.get()) {
                if (resetRequested.getAndSet(false)) {
                    resetTalkers(talkers)
                    inputQueue.clear()
                    if (outputStarted) stopOutput(track)
                    outputStarted = false
                }

                if (!outputStarted) {
                    inputQueue.poll(IDLE_POLL_MS, TimeUnit.MILLISECONDS)?.let { first ->
                        drainInput(talkers, first, MAX_INPUT_DRAIN_PER_TICK)
                    }
                    applyDiscontinuities(talkers)
                    val now = System.nanoTime()
                    removeIdleTalkers(talkers, now)
                    if (talkers.values.any { it.readyToStart(now) }) {
                        if (startOutput(track)) {
                            outputStarted = true
                            nextTickNanos = now
                        } else {
                            resetTalkers(talkers)
                            inputQueue.clear()
                        }
                    }
                    continue
                }

                var drained = 0
                while (
                    playbackControl.running.get() &&
                    !resetRequested.get() &&
                    drained < MAX_INPUT_DRAIN_PER_TICK
                ) {
                    val remaining = nextTickNanos - System.nanoTime()
                    if (remaining <= 0L) break
                    val queued = inputQueue.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                    ingestFrame(talkers, queued)
                    drained++
                    drained += drainAvailableInput(
                        talkers,
                        MAX_INPUT_DRAIN_PER_TICK - drained,
                    )
                }

                if (resetRequested.get()) continue
                drained += drainAvailableInput(
                    talkers,
                    MAX_INPUT_DRAIN_PER_TICK - drained,
                )
                applyDiscontinuities(talkers)

                val now = System.nanoTime()
                removeIdleTalkers(talkers, now)
                if (now - nextTickNanos > MAX_TICK_LAG_NANOS) {
                    nextTickNanos = now
                }

                val mixed = mixNextTick(talkers, now)
                if (mixed == null) {
                    talkers.values
                        .filter { it.isFinishedTalkspurt() }
                        .forEach(TalkerState::finishTalkspurt)
                    stopOutput(track)
                    outputStarted = false
                    continue
                }

                writeFully(track, mixed)
                nextTickNanos += MIX_TICK_NANOS
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (playbackControl.running.get()) {
                System.err.println("TS3_AUDIO: playback stopped: ${error.message}")
                error.printStackTrace(System.err)
            }
        } finally {
            playbackControl.running.set(false)
            resetTalkers(talkers)
            inputQueue.clear()
            runCatching { stopOutput(track) }
            runCatching { track.release() }
            synchronized(lifecycleLock) {
                if (control === playbackControl) control = null
                if (audioTrack === track) audioTrack = null
                if (worker === Thread.currentThread()) worker = null
            }
        }
    }

    private fun drainInput(
        talkers: MutableMap<Int, TalkerState>,
        first: QueuedFrame,
        limit: Int,
    ) {
        ingestFrame(talkers, first)
        drainAvailableInput(talkers, limit - 1)
    }

    private fun drainAvailableInput(
        talkers: MutableMap<Int, TalkerState>,
        limit: Int,
    ): Int {
        var count = 0
        while (count < limit) {
            val queued = inputQueue.poll() ?: break
            ingestFrame(talkers, queued)
            count++
        }
        return count
    }

    private fun ingestFrame(
        talkers: MutableMap<Int, TalkerState>,
        queued: QueuedFrame,
    ) {
        val clientId = queued.frame.clientId
        if (loggedFirstFrame.compareAndSet(false, true)) {
            System.err.println(
                "TS3_AUDIO: received first Opus frame from client $clientId " +
                    "(${queued.frame.encodedData.size} bytes)",
            )
        }
        if (discontinuityClients.remove(clientId)) {
            talkers.remove(clientId)?.close()
        }
        talkers.getOrPut(clientId, ::TalkerState).offer(queued)
    }

    private fun applyDiscontinuities(talkers: MutableMap<Int, TalkerState>) {
        val iterator = discontinuityClients.iterator()
        while (iterator.hasNext()) {
            val clientId = iterator.next()
            talkers.remove(clientId)?.close()
            iterator.remove()
        }
    }

    private fun mixNextTick(
        talkers: MutableMap<Int, TalkerState>,
        now: Long,
    ): ShortArray? {
        val inputs = ArrayList<ParticipantGainMixer.Input>(talkers.size)
        val iterator = talkers.iterator()
        while (iterator.hasNext()) {
            val (clientId, talker) = iterator.next()
            try {
                talker.fillPcm(now)
                talker.pcm.read(MIX_TICK_SAMPLES).takeIf { it.isNotEmpty() }?.let { samples ->
                    inputs += ParticipantGainMixer.Input(
                        samples = samples,
                        gain = participantGains[clientId] ?: DEFAULT_PARTICIPANT_GAIN,
                    )
                }
            } catch (error: RuntimeException) {
                System.err.println(
                    "TS3_AUDIO: dropping invalid Opus stream from client $clientId: ${error.message}",
                )
                talker.close()
                iterator.remove()
            }
        }

        val output = ParticipantGainMixer.mix(inputs, MIX_TICK_SAMPLES)
        if (output.isEmpty()) return null
        if (loggedFirstPcm.compareAndSet(false, true)) {
            System.err.println(
                "TS3_AUDIO: decoded ${decodedPacketCount.get()} packet(s), " +
                    "nonZero=${output.any { it.toInt() != 0 }}, " +
                    "received=${receivedFrameCount.get()}",
            )
        }
        return output
    }

    private fun removeIdleTalkers(talkers: MutableMap<Int, TalkerState>, now: Long) {
        val iterator = talkers.iterator()
        while (iterator.hasNext()) {
            val talker = iterator.next().value
            if (now - talker.lastArrivalNanos > TALKER_IDLE_NANOS) {
                talker.close()
                iterator.remove()
            }
        }
    }

    private fun resetTalkers(talkers: MutableMap<Int, TalkerState>) {
        talkers.values.forEach(TalkerState::close)
        talkers.clear()
        discontinuityClients.clear()
    }

    private fun startOutput(track: AudioTrack): Boolean {
        val focusResult = audioManager.requestAudioFocus(focusRequest)
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusAllowsPlayback.set(false)
            return false
        }
        focusAllowsPlayback.set(true)
        applyVolume()
        track.play()
        return true
    }

    private fun stopOutput(track: AudioTrack) {
        if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
            runCatching { track.pause() }
            runCatching { track.flush() }
        }
        audioManager.abandonAudioFocusRequest(focusRequest)
        focusAllowsPlayback.set(false)
        applyVolume()
    }

    private fun writeFully(track: AudioTrack, samples: ShortArray) {
        var offset = 0
        while (offset < samples.size) {
            val written = track.write(
                samples,
                offset,
                samples.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            if (written < 0) error("AudioTrack write failed: $written")
            if (written == 0) error("AudioTrack accepted no audio data")
            offset += written
        }
    }

    private fun createAudioTrack(): AudioTrack {
        val minimumBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBytes > 0) { "Unable to determine audio buffer size: $minimumBytes" }
        return AudioTrack.Builder()
            .setAudioAttributes(audioAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minimumBytes, TARGET_AUDIO_TRACK_BUFFER_BYTES))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
            .also { track ->
                preferredDevice?.let { device ->
                    if (!track.setPreferredDevice(device)) {
                        System.err.println("TS3_AUDIO: playback device preference was rejected")
                    }
                }
            }
    }

    private fun applyVolume() {
        val volume = if (userMuted.get() || !focusAllowsPlayback.get()) 0f else 1f
        runCatching { audioTrack?.setVolume(volume) }
    }

    private inner class TalkerState : AutoCloseable {
        val pcm = PcmFifo(PCM_FIFO_CAPACITY_SAMPLES)
        private val decoder = NativeOpusDecoder()
        private val pending = TreeMap<Long, QueuedFrame>()
        private var highestRaw: Int? = null
        private var highestExtended = 0L
        private var expectedSequence: Long? = null
        private var firstArrivalNanos: Long? = null
        private var started = false
        private var lastFrameSamples = DEFAULT_PACKET_SAMPLES
        private var missingRun = 0

        var lastArrivalNanos: Long = System.nanoTime()
            private set

        fun offer(queued: QueuedFrame) {
            val raw = queued.frame.packetId and PACKET_ID_MASK
            var extended = unwrap(raw)
            val expected = expectedSequence
            if (expected != null && extended - expected > MAX_FORWARD_PACKET_GAP) {
                resetPipeline(clearSequence = true)
                extended = unwrap(raw)
            }
            if (expectedSequence?.let { extended < it } == true || pending.containsKey(extended)) return
            if (pending.size >= MAX_PENDING_PACKETS) {
                resetPipeline(clearSequence = false)
            }

            pending[extended] = queued
            if (firstArrivalNanos == null) firstArrivalNanos = queued.arrivalNanos
            lastArrivalNanos = queued.arrivalNanos
        }

        fun readyToStart(now: Long): Boolean {
            if (pcm.availableSamples > 0) return true
            if (started) return pending.isNotEmpty()
            val firstArrival = firstArrivalNanos ?: return false
            return pending.isNotEmpty() && now - firstArrival >= INITIAL_HOLD_NANOS
        }

        fun fillPcm(now: Long) {
            if (!started) {
                if (!readyToStart(now)) return
                expectedSequence = pending.firstKey()
                started = true
            }

            while (pcm.availableSamples < PCM_TARGET_SAMPLES) {
                var expected = expectedSequence ?: break
                while (pending.isNotEmpty() && pending.firstKey() < expected) {
                    pending.pollFirstEntry()
                }

                val packet = pending.remove(expected)
                if (packet != null) {
                    val decoded = decoder.decode(packet.frame.encodedData)
                    if (decoded.isNotEmpty()) {
                        pcm.add(decoded)
                        lastFrameSamples = decoded.size
                        decodedPacketCount.incrementAndGet()
                    }
                    expectedSequence = expected + 1
                    missingRun = 0
                    continue
                }

                val nextSequence = pending.firstKeyOrNull() ?: break
                val gap = nextSequence - expected
                if (gap > MAX_CONCEALED_PACKETS || missingRun >= MAX_CONCEALED_PACKETS) {
                    decoder.reset()
                    expectedSequence = nextSequence
                    missingRun = 0
                    continue
                }
                if (pcm.availableSamples >= MIX_TICK_SAMPLES) break

                pcm.add(decoder.decode(null, lastFrameSamples))
                expected++
                expectedSequence = expected
                missingRun++
            }
        }

        fun isFinishedTalkspurt(): Boolean =
            started && pending.isEmpty() && pcm.availableSamples == 0

        fun finishTalkspurt() {
            decoder.reset()
            expectedSequence = null
            firstArrivalNanos = null
            started = false
            missingRun = 0
        }

        override fun close() {
            pending.clear()
            pcm.clear()
            decoder.close()
        }

        private fun unwrap(raw: Int): Long {
            val previousRaw = highestRaw
            if (previousRaw == null) {
                highestRaw = raw
                highestExtended = raw.toLong()
                return highestExtended
            }

            val delta = signedPacketDistance(previousRaw, raw)
            val extended = highestExtended + delta
            if (delta > 0) {
                highestRaw = raw
                highestExtended = extended
            }
            return extended
        }

        private fun resetPipeline(clearSequence: Boolean) {
            decoder.reset()
            pending.clear()
            pcm.clear()
            expectedSequence = null
            firstArrivalNanos = null
            started = false
            missingRun = 0
            lastFrameSamples = DEFAULT_PACKET_SAMPLES
            if (clearSequence) {
                highestRaw = null
                highestExtended = 0L
            }
        }
    }

    private class PcmFifo(private val capacity: Int) {
        private val chunks = ArrayDeque<ShortArray>()
        private var firstChunkOffset = 0

        var availableSamples: Int = 0
            private set

        fun add(samples: ShortArray) {
            val accepted = min(samples.size, capacity - availableSamples)
            if (accepted <= 0) return
            chunks.addLast(if (accepted == samples.size) samples else samples.copyOf(accepted))
            availableSamples += accepted
        }

        fun read(maxSamples: Int): ShortArray {
            val sampleCount = min(maxSamples, availableSamples)
            if (sampleCount == 0) return ShortArray(0)
            val output = ShortArray(sampleCount)
            var outputOffset = 0
            while (outputOffset < sampleCount) {
                val first = chunks.first()
                val copied = min(sampleCount - outputOffset, first.size - firstChunkOffset)
                first.copyInto(
                    destination = output,
                    destinationOffset = outputOffset,
                    startIndex = firstChunkOffset,
                    endIndex = firstChunkOffset + copied,
                )
                outputOffset += copied
                firstChunkOffset += copied
                availableSamples -= copied
                if (firstChunkOffset == first.size) {
                    chunks.removeFirst()
                    firstChunkOffset = 0
                }
            }
            return output
        }

        fun clear() {
            chunks.clear()
            firstChunkOffset = 0
            availableSamples = 0
        }
    }

    private data class QueuedFrame(
        val frame: VoiceFrame,
        val arrivalNanos: Long,
    )

    private class PlaybackControl {
        val running = AtomicBoolean(true)
    }

    companion object {
        private const val SAMPLE_RATE = 48_000
        private const val MIX_TICK_SAMPLES = 480
        private const val DEFAULT_PACKET_SAMPLES = 960
        private const val PCM_TARGET_SAMPLES = 2_880
        private const val PCM_FIFO_CAPACITY_SAMPLES = 5_760
        private const val INPUT_QUEUE_CAPACITY = 256
        private const val MAX_INPUT_DRAIN_PER_TICK = 256
        private const val MAX_PENDING_PACKETS = 64
        private const val MAX_CONCEALED_PACKETS = 3
        private const val MAX_FORWARD_PACKET_GAP = 64
        private const val PACKET_ID_MASK = 0xffff
        private const val IDLE_POLL_MS = 10L
        private const val STOP_JOIN_TIMEOUT_MS = 1_000L
        private const val TARGET_AUDIO_TRACK_BUFFER_BYTES = MIX_TICK_SAMPLES * 2 * 4
        private const val MIN_PARTICIPANT_GAIN = 0f
        private const val DEFAULT_PARTICIPANT_GAIN = 1f
        private const val MAX_PARTICIPANT_GAIN = 2f
        private val INITIAL_HOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(60)
        private val MIX_TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(10)
        private val MAX_TICK_LAG_NANOS = TimeUnit.MILLISECONDS.toNanos(50)
        private val TALKER_IDLE_NANOS = TimeUnit.SECONDS.toNanos(2)

        internal fun signedPacketDistance(previous: Int, current: Int): Int =
            ((current - previous + 0x8000) and PACKET_ID_MASK) - 0x8000

        private fun <V> TreeMap<Long, V>.firstKeyOrNull(): Long? =
            if (isEmpty()) null else firstKey()

        private fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
