package com.kankwj.angcode.connectors

import java.util.concurrent.ConcurrentHashMap

object ConnectorSessionRegistry {
    private val sessions = ConcurrentHashMap<String, McpClientPort>()

    fun put(id: String, client: McpClientPort) {
        sessions.put(id, client)?.let { previous ->
            if (previous !== client) runCatching { previous.close() }
        }
    }

    fun get(id: String): McpClientPort? = sessions[id]

    fun remove(id: String): Boolean {
        val client = sessions.remove(id) ?: return false
        return runCatching { client.close() }.getOrDefault(false)
    }

    fun ids(): Set<String> = sessions.keys.toSet()
}
