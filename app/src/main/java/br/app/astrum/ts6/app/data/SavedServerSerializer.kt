package br.app.astrum.ts6.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Utilitário para serialização e desserialização de listas de SavedServer em formato JSON.
 */
object SavedServerSerializer {

    fun serialize(servers: List<SavedServer>): String {
        val jsonArray = JSONArray()
        for (server in servers) {
            val jsonObject = JSONObject().apply {
                put("id", server.id)
                put("name", server.name)
                put("host", server.host)
                put("port", server.port)
                put("nickname", server.nickname)
                put("password", server.password)
                put("lastConnectedAt", server.lastConnectedAt)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    fun deserialize(jsonString: String?): List<SavedServer> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val list = ArrayList<SavedServer>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                list.add(
                    SavedServer(
                        id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        name = item.optString("name", ""),
                        host = item.optString("host", ""),
                        port = item.optInt("port", 9987),
                        nickname = item.optString("nickname", "TS6 Mobile"),
                        password = item.optString("password", ""),
                        lastConnectedAt = item.optLong("lastConnectedAt", 0L),
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
