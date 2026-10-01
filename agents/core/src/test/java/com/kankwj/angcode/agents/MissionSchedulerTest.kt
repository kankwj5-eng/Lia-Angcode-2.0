package com.kankwj.angcode.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionSchedulerTest {
    @Test
    fun lowMemoryReducesParallelWork() {
        val plan = MissionPlan(
            title = "test",
            tasks = listOf(
                MissionTask(title = "a", role = AgentRole.CODER),
                MissionTask(title = "b", role = AgentRole.TESTER),
                MissionTask(title = "c", role = AgentRole.BROWSER)
            )
        )

        val decision = MissionScheduler().schedule(
            plan,
            ResourceSnapshot(availableRamMb = 900, batteryPercent = 80, charging = false)
        )

        assertEquals(1, decision.runnable.size)
        assertEquals(2, decision.deferred.size)
    }

    @Test
    fun dependenciesBlockTasksUntilCompleted() {
        val first = MissionTask(title = "first", role = AgentRole.CODER)
        val second = MissionTask(title = "second", role = AgentRole.TESTER, dependsOn = setOf(first.id))
        val plan = MissionPlan(title = "deps", tasks = listOf(first, second))

        val decision = MissionScheduler().schedule(
            plan,
            ResourceSnapshot(4_000, 90, true)
        )

        assertEquals(listOf(first.id), decision.runnable.map { it.id })
        assertTrue(second !in decision.runnable)
    }
}
