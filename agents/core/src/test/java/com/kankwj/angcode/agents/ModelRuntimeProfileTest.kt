package com.kankwj.angcode.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRuntimeProfileTest {
    @Test
    fun lowMemoryUsesSavingsProfile() {
        val profile = ModelRuntimeProfileSelector.select(
            ResourceSnapshot(
                availableRamMb = 1_500,
                batteryPercent = 80,
                charging = false,
                thermalLevel = 0
            ),
            logicalCores = 8
        )

        assertEquals("ahorro", profile.id)
        assertEquals(2_048, profile.contextSize)
        assertTrue(profile.threads <= 2)
    }

    @Test
    fun thermalPressureForcesConservativeProfile() {
        val profile = ModelRuntimeProfileSelector.select(
            ResourceSnapshot(
                availableRamMb = 7_000,
                batteryPercent = 90,
                charging = true,
                thermalLevel = 4
            ),
            logicalCores = 8
        )

        assertEquals("ahorro", profile.id)
    }

    @Test
    fun healthyHighMemoryDeviceUsesWideProfile() {
        val profile = ModelRuntimeProfileSelector.select(
            ResourceSnapshot(
                availableRamMb = 6_500,
                batteryPercent = 90,
                charging = true,
                thermalLevel = 0
            ),
            logicalCores = 8
        )

        assertEquals("amplio", profile.id)
        assertEquals(8_192, profile.contextSize)
        assertTrue(profile.threads <= 6)
    }
}
