package br.app.astrum.ts6.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.serverDataStore by preferencesDataStore(name = "ts3_servers")
private val savedServersKey = stringPreferencesKey("saved_servers_json")
private val lastSelectedServerIdKey = stringPreferencesKey("last_selected_server_id")

/**
 * Armazena e gerencia a persistência dos servidores cadastrados usando DataStore Preferences.
 */
class ServerStore(private val context: Context) {

    /**
     * Fluxo com a lista de todos os servidores salvos, ordenados com o mais recente primeiro.
     */
    val servers: Flow<List<SavedServer>> = context.serverDataStore.data
        .catch { error ->
            if (error is IOException) {
                System.err.println("TS3_STORE: falha ao ler servidores salvos: ${error.message}")
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            val json = preferences[savedServersKey]
            SavedServerSerializer.deserialize(json)
        }

    /**
     * ID do último servidor selecionado ou conectado.
     */
    val lastSelectedServerId: Flow<String?> = context.serverDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> preferences[lastSelectedServerIdKey] }

    /**
     * Salva ou atualiza um servidor na lista persistida.
     */
    suspend fun saveServer(server: SavedServer) {
        context.serverDataStore.edit { preferences ->
            val currentList = SavedServerSerializer.deserialize(preferences[savedServersKey]).toMutableList()
            val existingIndex = currentList.indexOfFirst { it.id == server.id }
            if (existingIndex >= 0) {
                currentList[existingIndex] = server
            } else {
                currentList.add(server)
            }
            preferences[savedServersKey] = SavedServerSerializer.serialize(currentList)
            preferences[lastSelectedServerIdKey] = server.id
        }
    }

    /**
     * Remove um servidor pelo seu identificador.
     */
    suspend fun deleteServer(serverId: String) {
        context.serverDataStore.edit { preferences ->
            val currentList = SavedServerSerializer.deserialize(preferences[savedServersKey]).toMutableList()
            currentList.removeAll { it.id == serverId }
            preferences[savedServersKey] = SavedServerSerializer.serialize(currentList)
            if (preferences[lastSelectedServerIdKey] == serverId) {
                preferences[lastSelectedServerIdKey] = currentList.firstOrNull()?.id ?: ""
            }
        }
    }

    /**
     * Define o servidor selecionado atualmente.
     */
    suspend fun setLastSelectedServerId(serverId: String) {
        context.serverDataStore.edit { preferences ->
            preferences[lastSelectedServerIdKey] = serverId
        }
    }

    /**
     * Registra o timestamp da conexão com o servidor selecionado para manter histórico de uso.
     */
    suspend fun recordConnected(serverId: String) {
        context.serverDataStore.edit { preferences ->
            val currentList = SavedServerSerializer.deserialize(preferences[savedServersKey]).toMutableList()
            val existingIndex = currentList.indexOfFirst { it.id == serverId }
            if (existingIndex >= 0) {
                currentList[existingIndex] = currentList[existingIndex].copy(
                    lastConnectedAt = System.currentTimeMillis(),
                )
                preferences[savedServersKey] = SavedServerSerializer.serialize(currentList)
            }
            preferences[lastSelectedServerIdKey] = serverId
        }
    }
}
