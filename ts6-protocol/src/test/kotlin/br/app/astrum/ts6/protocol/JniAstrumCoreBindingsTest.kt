package br.app.astrum.ts6.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JniAstrumCoreBindingsTest {
    @Test
    fun nullPollResponseIsTimeout() {
        assertEquals(NativePollResult.Timeout, mapNativePollResult(null))
    }

    @Test
    fun closedPollSentinelsAreClosed() {
        assertEquals(
            NativePollResult.Closed,
            mapNativePollResult("{\"type\":\"__astrum_jni_receiver_closed__\"}"),
        )
        assertEquals(
            NativePollResult.Closed,
            mapNativePollResult("{\"type\":\"__astrum_jni_session_closed__\"}"),
        )
    }

    @Test
    fun serializationPollSentinelIsErrorWithClearCause() {
        val result = mapNativePollResult("{\"type\":\"__astrum_jni_serialization_error__\"}")
        val error = result as? NativePollResult.Error

        assertTrue(error != null)
        assertTrue(error?.cause?.message?.contains("serialization") == true)
    }

    @Test
    fun nonSentinelPollJsonRemainsEvent() {
        val json = "{\"type\":\"Ready\",\"data\":{}}"

        assertEquals(NativePollResult.Event(json), mapNativePollResult(json))
    }
}
