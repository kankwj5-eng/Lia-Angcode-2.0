package com.kankwj.angcode.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidBridgeCatalogTest {
    @Test
    fun catalogExposesNetworkAndSensors() {
        val ids = ToolCatalog.capabilities.map { it.id }.toSet()
        assertTrue("android.network" in ids)
        assertTrue("android.sensors.list" in ids)
    }
}
