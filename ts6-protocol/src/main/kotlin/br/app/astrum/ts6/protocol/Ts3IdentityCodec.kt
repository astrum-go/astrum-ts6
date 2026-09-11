package br.app.astrum.ts6.protocol

import com.github.manevolent.ts3j.identity.LocalIdentity
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

object Ts3IdentityCodec {
    fun generate(securityLevel: Int = 10): String =
        LocalIdentity.generateNew(securityLevel).export()

    internal fun decode(material: String): LocalIdentity =
        ByteArrayInputStream(material.toByteArray(StandardCharsets.UTF_8)).use(LocalIdentity::read)
}

