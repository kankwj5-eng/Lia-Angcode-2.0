package com.kankwj.angcode.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalResourceSchedulerTest {
    private val plan = MissionPlan(
        title = "resources",
        tasks = listOf(
            MissionTask(title = "r1", role = AgentRole.RESEARCHER),
            MissionTask(title = "r2", role = AgentRole.BROWSER)
        )
    )

    @Test
    fun pausesWhenAvailableRamIsCritical() {
        val decision = MissionScheduler().schedule(
            plan,
            ResourceSnapshot(128, 80, false, 0)
        )
        assertTrue(decision.runnable.isEmpty())
        assertEquals(2, decision.deferred.size)
        assertTrue(decision.reason.contains("ram crítica"))
    }

    @Test
    fun pausesAtCriticalThermalLevel() {
        val decision = MissionScheduler().schedule(
            plan,
            ResourceSnapshot(4096, 80, false, 4)
        )
        assertTrue(decision.runnable.isEmpty())
        assertTrue(decision.reason.contains("térmica crítica"))
    }

    @Test
    fun pausesAtCriticalBatteryWhenNotCharging() {
        val decision = MissionScheduler().schedule(
            plan,
            ResourceSnapshot(4096, 5, false, 0)
        )
        assertTrue(decision.runnable.isEmpty())
        assertTrue(decision.reason.contains("batería crítica"))
    }
}
