package br.app.astrum.ts6.protocol

import com.github.manevolent.ts3j.command.CommandException
import com.github.manevolent.ts3j.command.CommandProcessException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

class ConnectionFailureTest {
    @Test
    fun networkFailuresAreRetryableThroughWrappedCauses() {
        assertTrue(IOException("offline").isRetryableConnectionFailure())
        assertTrue(SocketException("reset").isRetryableConnectionFailure())
        assertTrue(UnknownHostException("host").isRetryableConnectionFailure())
        assertTrue(IllegalStateException(TimeoutException("slow")).isRetryableConnectionFailure())
        assertTrue(RuntimeException(InterruptedException("handshake interrupted")).isRetryableConnectionFailure())
    }

    @Test
    fun protocolAndConfigurationFailuresAreTerminal() {
        assertFalse(CommandException("invalid password", 520).isRetryableConnectionFailure())
        assertFalse(CommandProcessException("nickname is already in use").isRetryableConnectionFailure())
        assertFalse(IllegalArgumentException("bad port").isRetryableConnectionFailure())
    }

    @Test
    fun staleCloneRejectionIsRetryableAfterAConnectionDrop() {
        assertTrue(
            CommandProcessException("too many clones already connected")
                .isRetryableConnectionFailure(),
        )
    }
}
