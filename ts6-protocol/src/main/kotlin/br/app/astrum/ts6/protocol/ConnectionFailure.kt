package br.app.astrum.ts6.protocol

import com.github.manevolent.ts3j.command.CommandException
import com.github.manevolent.ts3j.command.CommandProcessException
import java.io.IOException
import java.util.concurrent.TimeoutException

fun Throwable.isRetryableConnectionFailure(): Boolean {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    var networkFailureFound = false
    while (current != null && visited.add(current)) {
        if (current is CommandProcessException) {
            return current.message?.contains(TRANSIENT_CLONE_ERROR, ignoreCase = true) == true
        }
        if (current is CommandException) return false
        if (
            current is IOException ||
            current is TimeoutException ||
            current is InterruptedException
        ) {
            networkFailureFound = true
        }
        current = current.cause
    }
    return networkFailureFound
}

private const val TRANSIENT_CLONE_ERROR = "too many clones already connected"
