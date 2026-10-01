package com.kankwj.angcode.agents

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MissionAnalyzerTest {
    @Test
    fun detectsAndroidBuildMissionAndPlansBuildFlow() {
        val root = createTempDirectory("angcode-mission-").toFile()
        File(root, "settings.gradle.kts").writeText("rootProject.name = \"Demo\"")
        File(root, "app/src/main").mkdirs()
        File(root, "app/src/main/AndroidManifest.xml").writeText("<manifest/>")

        val analysis = MissionAnalyzer().analyze(
            "Corrige el bug, prueba la app y compila el APK",
            root
        )

        assertEquals(ProjectKind.ANDROID, analysis.project.kind)
        assertTrue("android-dev" in analysis.recommendedToolPacks)
        assertTrue(MissionCapability.EDIT_CODE in analysis.capabilities)
        assertTrue(MissionCapability.TEST in analysis.capabilities)
        assertTrue(MissionCapability.BUILD in analysis.capabilities)
        assertTrue(analysis.plan.tasks.any { it.role == AgentRole.BUILDER })
    }

    @Test
    fun detectsBrowserResearch() {
        val root = createTempDirectory("angcode-web-mission-").toFile()
        val analysis = MissionAnalyzer().analyze(
            "Busca documentación en la web y abre el navegador",
            root
        )

        assertTrue(MissionCapability.WEB_RESEARCH in analysis.capabilities)
        assertTrue(MissionCapability.BROWSER_AUTOMATION in analysis.capabilities)
        assertTrue("lightpanda-browser" in analysis.recommendedToolPacks)
    }
}
