package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolContext
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCancellationTest {
    @Test
    fun engineStopsBeforeCallingModelWhenAlreadyCancelled() {
        var modelCalled = false
        val model = ModelGateway {
            modelCalled = true
            ModelResponse(text = "should-not-run")
        }
        val token = AgentCancellationToken().apply { cancel() }
        val root = createTempDirectory("angcode-cancel-").toFile()

        val result = ToolCallingAgentEngine(model, ToolBroker()).run(
            task = "cancelled",
            context = ToolContext(root, emptySet()),
            cancellation = token
        )

        assertTrue(result.cancelled)
        assertFalse(result.completed)
        assertFalse(modelCalled)
    }
    @Test
    fun markCancelledClearsWorkingAndQueuedStates() {
        val running = MissionTask(
            title = "running",
            role = AgentRole.CODER,
            status = AgentStatus.WORKING
        )
        val queued = MissionTask(
            title = "queued",
            role = AgentRole.TESTER,
            status = AgentStatus.QUEUED,
            dependsOn = setOf(running.id)
        )
        val done = MissionTask(
            title = "done",
            role = AgentRole.RESEARCHER,
            status = AgentStatus.DONE
        )
        val analysis = MissionAnalysis(
            mission = "test",
            project = ProjectProfile(ProjectKind.GENERIC, emptyList(), emptyList()),
            capabilities = setOf(MissionCapability.INSPECT),
            recommendedToolPacks = emptyList(),
            plan = MissionPlan(tasks = listOf(running, queued, done), title = "test")
        )
        val session = MissionSession(
            analysis = analysis,
            tasks = listOf(running, queued, done).map(::MissionTaskState)
        )

        val cancelled = MissionStateMachine().markCancelled(session, nowMillis = 123L)

        assertTrue(cancelled.tasks.none {
            it.task.status == AgentStatus.WORKING ||
                it.task.status == AgentStatus.QUEUED
        })
        assertTrue(cancelled.tasks.count { it.task.status == AgentStatus.FAILED } == 2)
        assertTrue(cancelled.tasks.single { it.task.id == done.id }.task.status == AgentStatus.DONE)
    }
}
