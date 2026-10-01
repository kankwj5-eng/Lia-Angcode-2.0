package com.kankwj.angcode.agents

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

data class AgentEvent(
    val type: String,
    val source: String,
    val payload: Map<String, String> = emptyMap(),
    val createdAt: Instant = Instant.now()
)

fun interface EventListener {
    fun onEvent(event: AgentEvent)
}

class EventBus {
    private val listeners = CopyOnWriteArrayList<EventListener>()
    private val history = CopyOnWriteArrayList<AgentEvent>()

    fun subscribe(listener: EventListener): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    fun publish(event: AgentEvent) {
        history += event
        listeners.forEach { it.onEvent(event) }
    }

    fun history(limit: Int = 100): List<AgentEvent> = history.takeLast(limit)
}
