package com.kankwj.angcode.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogConsistencyTest {
    @Test
    fun everyCoreRegisteredToolIsRepresentedInCatalog() {
        val registered = ToolBroker()
            .registerCoreTools()
            .availableTools()
            .map { it.id }
            .toSet()

        val catalogIds = ToolCatalog.capabilities.map { it.id }

        val missing = registered.filterNot { toolId ->
            catalogIds.any { capabilityId ->
                when {
                    capabilityId == toolId -> true
                    capabilityId.endsWith(".*") ->
                        toolId.startsWith(capabilityId.removeSuffix("*"))
                    else -> false
                }
            }
        }

        assertTrue(
            "Herramientas registradas sin catálogo: " + missing.joinToString(),
            missing.isEmpty()
        )
    }

    @Test
    fun builtInCoreCapabilitiesAreActuallyRegistered() {
        val registered = ToolBroker()
            .registerCoreTools()
            .availableTools()
            .map { it.id }
            .toSet()

        val coreFamilies = setOf(
            ToolFamily.FILES,
            ToolFamily.PROCESS,
            ToolFamily.NETWORK,
            ToolFamily.ARCHIVE,
            ToolFamily.VERSION_CONTROL,
            ToolFamily.CODE_INTELLIGENCE,
            ToolFamily.SANDBOX
        )

        val missing = ToolCatalog.capabilities
            .filter { it.status == CapabilityStatus.BUILT_IN }
            .filter { it.family in coreFamilies }
            .filterNot { it.id.endsWith(".*") }
            .map { it.id }
            .filterNot { it in registered }

        assertTrue(
            "Capacidades BUILT_IN sin herramienta core: " + missing.joinToString(),
            missing.isEmpty()
        )
    }
}
