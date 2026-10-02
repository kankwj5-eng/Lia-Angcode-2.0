package com.kankwj.angcode.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRulesTest {
    private fun valid() = LocalAutomation(
        id = "a-1",
        name = "Revisar proyecto",
        mission = "Ejecuta pruebas y reporta errores.",
        workspaceName = "demo",
        intervalMinutes = 60,
        requireNetwork = false,
        requireCharging = false
    )

    @Test
    fun acceptsValidAutomation() {
        assertTrue(AutomationRules.validate(valid()).isEmpty())
    }

    @Test
    fun rejectsIntervalsBelowWorkManagerMinimum() {
        val errors = AutomationRules.validate(
            valid().copy(intervalMinutes = 14)
        )
        assertTrue(errors.any { it.contains("intervalMinutes") })
    }

    @Test
    fun rejectsWorkspaceTraversal() {
        val errors = AutomationRules.validate(
            valid().copy(workspaceName = "../otro")
        )
        assertFalse(errors.isEmpty())
    }

    @Test
    fun rejectsOversizedMission() {
        val errors = AutomationRules.validate(
            valid().copy(
                mission = "x".repeat(AutomationRules.MAX_MISSION_CHARS + 1)
            )
        )
        assertTrue(errors.any { it.contains("mission") })
    }

    @Test
    fun normalizesWhitespaceAndIntervalBounds() {
        val normalized = AutomationRules.normalized(
            valid().copy(
                name = "  Nombre  ",
                mission = "  Hazlo  ",
                intervalMinutes = 1
            )
        )
        assertTrue(normalized.name == "Nombre")
        assertTrue(normalized.mission == "Hazlo")
        assertTrue(
            normalized.intervalMinutes == AutomationRules.MIN_INTERVAL_MINUTES
        )
    }
}
