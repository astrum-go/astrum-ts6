package br.app.astrum.ts6.app.data

import br.app.astrum.ts6.protocol.ServerConfig
import java.util.UUID

/**
 * Representa um servidor TeamSpeak salvo localmente pelo usuário.
 */
data class SavedServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 9987,
    val nickname: String = "TS3 Mobile",
    val password: String = "",
    val lastConnectedAt: Long = 0L,
) {
    /**
     * Nome para exibição na interface: usa o nome amigável ou o próprio host caso vazio.
     */
    val displayName: String
        get() = name.trim().ifBlank { host.trim().ifBlank { "Servidor TeamSpeak" } }

    /**
     * Formata o host e porta para visualização simples.
     */
    val hostPortDisplay: String
        get() = if (port == 9987) host.trim() else "${host.trim()}:$port"

    /**
     * Converte para a configuração de servidor do protocolo TS3.
     */
    fun toServerConfig(): ServerConfig {
        return ServerConfig(
            host = host.trim(),
            port = port,
            nickname = nickname.trim().ifBlank { "TS3 Mobile" },
            password = password,
        ).normalized()
    }

    /**
     * Valida os campos obrigatórios e formato.
     */
    fun validationError(): String? {
        if (host.trim().isEmpty()) return "Digite o endereço do servidor"
        if (port !in 1..65535) return "A porta deve estar entre 1 e 65535"
        val nick = nickname.trim()
        if (nick.length !in 3..30) return "O apelido deve ter entre 3 e 30 caracteres"
        return null
    }
}
