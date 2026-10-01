package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveMissionCoordinatorTest {
    @Test
    fun completesSimpleDependentPlanWithSerializedModel() {
        val root = createTempDirectory("angcode-coordinator-").toFile()
        val first = MissionTask(
            title = "Inspeccionar",
            role = AgentRole.RESEARCHER
        )
        val second = MissionTask(
            title = "Implementar",
            role = AgentRole.CODER,
            dependsOn = setOf(first.id)
        )
        val plan = MissionPlan(
            title = "test",
            tasks = listOf(first, second)
        )
        val analysis = MissionAnalysis(
            mission = "hazlo",
            project = ProjectProfile(
                ProjectKind.GENERIC,
                listOf("test"),
                listOf("core-dev")
            ),
            capabilities = setOf(MissionCapability.INSPECT),
            recommendedToolPacks = listOf("core-dev"),
            plan = plan
        )

        val fakeModel = ModelGateway {
            ModelResponse(text = """{"type":"final","text":"ok"}""")
        }
        val coordinator = AdaptiveMissionCoordinator(
            model = fakeModel,
            broker = ToolBroker()
        )

        val result = coordinator.run(
            initial = MissionStateMachine().create(analysis),
            rootContext = ToolContext(
                workspace = root,
                grantedPermissions = setOf(
                    ToolPermission.WORKSPACE_READ,
                    ToolPermission.WORKSPACE_WRITE,
                    ToolPermission.PROCESS_EXECUTE,
                    ToolPermission.NETWORK
                )
            ),
            resources = ResourceSnapshot(
                availableRamMb = 4_000,
                batteryPercent = 90,
                charging = true
            ),
            config = AgentRunConfig(maxSteps = 1, planningInterval = 0)
        )

        assertTrue(result.completed)
        assertEquals(2, result.taskResults.size)
    }
}
