package br.app.astrum.ts6.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AstrumCoreSessionClientTest {
    @Test
    fun connectedIsNotPublicUntilReady() {
        val native = FakeBindings()
        val listener = RecordingListener()
        val client = AstrumCoreSessionClient(native)
        val connect = connectInThread(client, native, listener)

        native.emit(event("Connected", "{\"own_client_id\":7}"))
        assertTrue(native.connectedPolled.await(1000, TimeUnit.MILLISECONDS))
        native.emit(NativePollResult.Timeout)
        assertTrue(native.timeoutPolled.await(1000, TimeUnit.MILLISECONDS))
        assertEquals(listOf(ConnectionPhase.CONNECTING), listener.statuses.map { it.phase })

        native.emit(event("Ready", "{}"))
        connect.join(1000)
        assertFalse(connect.isAlive)
        assertEquals(ConnectionPhase.CONNECTED, listener.statuses.last().phase)
        client.close()
    }

    @Test
    fun nativeTerminalPollResultsAreNotConfirmedDisconnects() {
        for ((terminal, expectedDetail) in listOf(
            NativePollResult.ReceiverClosed to "receiver closed",
            NativePollResult.SessionClosed to "session is missing",
        )) {
            val native = FakeBindings()
            val listener = RecordingListener()
            val client = AstrumCoreSessionClient(native)
            val connect = connectInThread(client, native, listener)
            native.emit(event("Connected", "{\"own_client_id\":7}"))
            native.emit(terminal)

            connect.join(1000)
            assertFalse(connect.isAlive)
            val status = listener.statuses.last { it.phase == ConnectionPhase.ERROR }
            assertTrue(status.detail?.contains(expectedDetail) == true)
            assertEquals(DisconnectResult.Failure, status.disconnectResult)
            assertTrue(status.disconnectResult != DisconnectResult.Confirmed)
            client.close()
        }

        val closedNative = FakeBindings()
        val closedListener = RecordingListener()
        val closedClient = AstrumCoreSessionClient(closedNative)
        val closedConnect = connectInThread(closedClient, closedNative, closedListener)
        closedNative.emit(event("Connected", "{\"own_client_id\":7}"))
        closedNative.emit(NativePollResult.ReceiverClosed)
        closedConnect.join(1000)
        assertFalse(closedConnect.isAlive)
        assertTrue(closedListener.statuses.any { it.phase == ConnectionPhase.ERROR })
        closedClient.close()

        val errorNative = FakeBindings()
        val errorListener = RecordingListener()
        val errorClient = AstrumCoreSessionClient(errorNative)
        val errorConnect = connectInThread(errorClient, errorNative, errorListener)
        errorNative.emit(event("Connected", "{\"own_client_id\":7}"))
        errorNative.emit(NativePollResult.Error(IOException("poll boom")))
        errorConnect.join(1000)
        assertFalse(errorConnect.isAlive)
        assertTrue(errorListener.statuses.any { it.phase == ConnectionPhase.ERROR })
        errorClient.close()
    }

    @Test
    fun disconnectResultIsPreservedInTerminalStatus() {
        for ((result, expectedPhase) in listOf(
            1 to ConnectionPhase.DISCONNECTED,
            0 to ConnectionPhase.ERROR,
            -1 to ConnectionPhase.ERROR,
        )) {
            val native = FakeBindings(disconnectResult = result)
            val listener = RecordingListener()
            val client = AstrumCoreSessionClient(native)
            connectReady(client, native, listener)
            client.disconnect("test disconnect")

            val terminal = listener.statuses.last()
            assertEquals(expectedPhase, terminal.phase)
            assertEquals(
                when (result) {
                    1 -> DisconnectResult.Confirmed
                    0 -> DisconnectResult.TimedOut
                    else -> DisconnectResult.Failure
                },
                terminal.disconnectResult,
            )
            client.close()
        }
    }

    @Test
    fun sendVoiceFalseIsReportedAndTerminatesTheSession() {
        val native = FakeBindings(sendVoiceResult = false)
        val listener = RecordingListener()
        val client = AstrumCoreSessionClient(native)
        client.setVoiceSource(object : EncodedVoiceSource {
            override fun isReady() = true
            override fun pollEncodedFrame() = byteArrayOf(1, 2, 3)
        })
        connectReady(client, native, listener)

        assertTrue(native.voiceAttempt.await(1000, TimeUnit.MILLISECONDS))
        assertTrue(listener.statuses.any {
            it.phase == ConnectionPhase.ERROR && it.detail?.contains("sendVoice") == true
        })
        client.close()
    }

    private fun connectReady(
        client: AstrumCoreSessionClient,
        native: FakeBindings,
        listener: RecordingListener,
    ) {
        val thread = connectInThread(client, native, listener)
        native.emit(event("Connected", "{\"own_client_id\":7}"))
        native.emit(event("Ready", "{}"))
        thread.join(1000)
        assertFalse(thread.isAlive)
    }

    private fun connectInThread(
        client: AstrumCoreSessionClient,
        native: FakeBindings,
        listener: RecordingListener,
    ): Thread = Thread {
        runCatching {
            client.connect(ServerConfig("localhost", nickname = "Tester"), "identity", listener)
        }
    }.also {
        it.start()
        assertTrue(native.connectStarted.await(1000, TimeUnit.MILLISECONDS))
    }

    private fun event(type: String, data: String): NativePollResult =
        NativePollResult.Event("{\"type\":\"$type\",\"data\":$data}")

    private class RecordingListener : Ts3SessionListener {
        val statuses = CopyOnWriteArrayList<ConnectionStatus>()

        override fun onStatusChanged(status: ConnectionStatus) {
            statuses += status
        }

        override fun onSnapshotChanged(snapshot: SessionSnapshot) = Unit
        override fun onVoiceFrame(frame: VoiceFrame) = Unit
    }

    private class FakeBindings(
        private val disconnectResult: Int = 1,
        private val sendVoiceResult: Boolean = true,
    ) : AstrumCoreBindings {
        val connectStarted = CountDownLatch(1)
        val connectedPolled = CountDownLatch(1)
        val timeoutPolled = CountDownLatch(1)
        val voiceAttempt = CountDownLatch(1)
        private val polls = LinkedBlockingQueue<NativePollResult>()

        override fun connectWithIdentity(host: String, port: Int, nickname: String, identityMaterial: String): Long {
            connectStarted.countDown()
            return 42L
        }

        override fun pollEvent(sessionId: Long, timeoutMs: Long): NativePollResult {
            val result = polls.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: NativePollResult.Timeout
            if (result is NativePollResult.Event && result.json.contains("\"Connected\"")) {
                connectedPolled.countDown()
            }
            if (result === NativePollResult.Timeout) timeoutPolled.countDown()
            return result
        }

        override fun sendVoice(sessionId: Long, codec: Int, data: ByteArray): Boolean {
            voiceAttempt.countDown()
            return sendVoiceResult
        }

        override fun disconnectWithReason(sessionId: Long, reason: String): Int = disconnectResult

        fun emit(result: NativePollResult) {
            polls.offer(result)
        }
    }
}
