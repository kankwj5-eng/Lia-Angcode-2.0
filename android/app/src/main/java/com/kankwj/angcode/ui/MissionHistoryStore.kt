package com.kankwj.angcode.ui

import com.kankwj.angcode.agents.MissionAnalysis
import com.kankwj.angcode.agents.MissionCoordinatorResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class MissionHistoryEntry(
    val id: String,
    val mission: String,
    val projectKind: String,
    val modelName: String,
    val completed: Boolean,
    val stalled: Boolean,
    val createdAtMillis: Long,
    val taskCount: Int,
    val successfulTasks: Int,
    val file: File
)

class MissionHistoryStore {
    fun save(
        workspace: File,
        analysis: MissionAnalysis,
        result: MissionCoordinatorResult,
        modelName: String
    ): MissionHistoryEntry {
        val historyDir = File(workspace, ".agent/history").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val id = now.toString() + "-" + UUID.randomUUID().toString().take(8)
        val file = File(historyDir, id + ".json")

        val tasks = JSONArray()
        result.taskResults.forEach { task ->
            tasks.put(
                JSONObject()
                    .put("taskId", task.taskId)
                    .put("role", task.role.name)
                    .put("completed", task.completed)
                    .put("answer", task.answer)
                    .put("stepsUsed", task.stepsUsed)
            )
        }

        val root = JSONObject()
            .put("id", id)
            .put("mission", analysis.mission)
            .put("projectKind", analysis.project.kind.name)
            .put("model", modelName)
            .put("completed", result.completed)
            .put("stalled", result.stalled)
            .put("createdAtMillis", now)
            .put("recommendedToolPacks", JSONArray(analysis.recommendedToolPacks))
            .put("tasks", tasks)

        file.writeText(root.toString(2))

        return toEntry(file, root)
    }

    fun list(workspace: File, limit: Int = 30): List<MissionHistoryEntry> {
        val historyDir = File(workspace, ".agent/history")
        return historyDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .sortedByDescending { it.lastModified() }
            .take(limit.coerceIn(1, 200))
            .mapNotNull { file ->
                runCatching {
                    toEntry(file, JSONObject(file.readText()))
                }.getOrNull()
            }
    }

    fun read(entry: MissionHistoryEntry): JSONObject =
        JSONObject(entry.file.readText())

    private fun toEntry(file: File, root: JSONObject): MissionHistoryEntry {
        val tasks = root.optJSONArray("tasks") ?: JSONArray()
        var successful = 0
        for (index in 0 until tasks.length()) {
            if (tasks.optJSONObject(index)?.optBoolean("completed") == true) successful++
        }

        return MissionHistoryEntry(
            id = root.optString("id", file.nameWithoutExtension),
            mission = root.optString("mission"),
            projectKind = root.optString("projectKind", "GENERIC"),
            modelName = root.optString("model"),
            completed = root.optBoolean("completed"),
            stalled = root.optBoolean("stalled"),
            createdAtMillis = root.optLong("createdAtMillis", file.lastModified()),
            taskCount = tasks.length(),
            successfulTasks = successful,
            file = file
        )
    }
}
