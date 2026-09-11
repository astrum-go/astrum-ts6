package br.app.astrum.ts6.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.app.astrum.ts6.app.data.SavedServer
import br.app.astrum.ts6.app.data.ServerStore
import br.app.astrum.ts6.protocol.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class MainTab {
    SAVED_SERVERS,
    QUICK_CONNECT,
}

data class ConnectionFormState(
    val host: String = "",
    val port: String = "9987",
    val nickname: String = "TS6 Mobile",
    val password: String = "",
    val saveToList: Boolean = true,
    val serverName: String = "",
    val submitted: Boolean = false,
) {
    fun toServerConfigOrNull(): ServerConfig? {
        val config = ServerConfig(
            host = host,
            port = port.toIntOrNull() ?: return null,
            nickname = nickname,
            password = password,
        ).normalized()
        return config.takeIf { it.validationError() == null }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val serverStore = ServerStore(application)

    val savedServers: StateFlow<List<SavedServer>> = serverStore.servers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val lastSelectedServerId: StateFlow<String?> = serverStore.lastSelectedServerId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    private val mutableActiveTab = MutableStateFlow(MainTab.SAVED_SERVERS)
    val activeTab: StateFlow<MainTab> = mutableActiveTab.asStateFlow()

    private val mutableQuickForm = MutableStateFlow(ConnectionFormState())
    val quickForm: StateFlow<ConnectionFormState> = mutableQuickForm.asStateFlow()

    // Servidor atualmente em edição ou criação (null = modal fechado)
    private val mutableEditingServer = MutableStateFlow<SavedServer?>(null)
    val editingServer: StateFlow<SavedServer?> = mutableEditingServer.asStateFlow()

    private val mutableIsCreatingNew = MutableStateFlow(false)
    val isCreatingNew: StateFlow<Boolean> = mutableIsCreatingNew.asStateFlow()

    fun setActiveTab(tab: MainTab) {
        mutableActiveTab.value = tab
    }

    fun openAddServerDialog() {
        val defaultNickname = quickForm.value.nickname.ifBlank { "TS6 Mobile" }
        mutableEditingServer.value = SavedServer(
            id = UUID.randomUUID().toString(),
            name = "",
            host = "",
            port = 9987,
            nickname = defaultNickname,
            password = "",
        )
        mutableIsCreatingNew.value = true
    }

    fun openEditServerDialog(server: SavedServer) {
        mutableEditingServer.value = server
        mutableIsCreatingNew.value = false
    }

    fun dismissEditorDialog() {
        mutableEditingServer.value = null
        mutableIsCreatingNew.value = false
    }

    fun saveEditingServer(server: SavedServer, connectImmediately: Boolean = false): ServerConfig? {
        val validation = server.validationError()
        if (validation != null) return null

        viewModelScope.launch {
            serverStore.saveServer(server)
            if (connectImmediately) {
                serverStore.recordConnected(server.id)
            }
        }
        mutableEditingServer.value = null
        mutableIsCreatingNew.value = false

        return if (connectImmediately) server.toServerConfig() else null
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            serverStore.deleteServer(serverId)
        }
    }

    fun connectToSavedServer(server: SavedServer): ServerConfig {
        viewModelScope.launch {
            serverStore.recordConnected(server.id)
        }
        return server.toServerConfig()
    }

    fun selectServer(serverId: String) {
        viewModelScope.launch {
            serverStore.setLastSelectedServerId(serverId)
        }
    }

    // Formulário de conexão rápida
    fun setQuickHost(value: String) = updateQuick { copy(host = value, submitted = false) }
    fun setQuickPort(value: String) = updateQuick { copy(port = value.filter(Char::isDigit), submitted = false) }
    fun setQuickNickname(value: String) = updateQuick { copy(nickname = value, submitted = false) }
    fun setQuickPassword(value: String) = updateQuick { copy(password = value, submitted = false) }
    fun setQuickSaveToList(value: Boolean) = updateQuick { copy(saveToList = value) }
    fun setQuickServerName(value: String) = updateQuick { copy(serverName = value) }

    fun submitQuickConnect(): ServerConfig? {
        mutableQuickForm.update { it.copy(submitted = true) }
        val current = mutableQuickForm.value
        val config = current.toServerConfigOrNull() ?: return null

        if (current.saveToList) {
            val newServer = SavedServer(
                name = current.serverName.trim(),
                host = config.host,
                port = config.port,
                nickname = config.nickname,
                password = config.password,
                lastConnectedAt = System.currentTimeMillis(),
            )
            viewModelScope.launch {
                serverStore.saveServer(newServer)
            }
        }
        return config
    }

    private inline fun updateQuick(transform: ConnectionFormState.() -> ConnectionFormState) {
        mutableQuickForm.update { it.transform() }
    }
}
