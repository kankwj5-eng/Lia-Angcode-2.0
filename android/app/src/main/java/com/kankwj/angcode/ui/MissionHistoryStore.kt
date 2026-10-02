package com.kankwj.angcode.ui

import com.kankwj.angcode.agents.AgentRole
import com.kankwj.angcode.agents.AgentStatus
import com.kankwj.angcode.agents.MissionAnalysis
import com.kankwj.angcode.agents.MissionAnalyzer
import com.kankwj.angcode.agents.MissionCapability
import com.kankwj.angcode.agents.MissionCoordinatorResult
import com.kankwj.angcode.agents.MissionPlan
import com.kankwj.angcode.agents.MissionSession
import com.kankwj.angcode.agents.MissionStateMachine
import com.kankwj.angcode.agents.MissionTask
import com.kankwj.angcode.agents.MissionTaskState
import com.kankwj.angcode.agents.ProjectKind
import com.kankwj.angcode.agents.ProjectProfile
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

        val taskResults = JSONArray()
        result.taskResults.forEach { task ->
            taskResults.put(
                JSONObject()
                    .put("taskId", task.taskId)
                    .put("role", task.role.name)
                    .put("completed", task.completed)
                    .put("answer", task.answer)
                    .put("stepsUsed", task.stepsUsed)
            )
        }

        val root = JSONObject()
            .put("snapshotVersion", SNAPSHOT_VERSION)
            .put("id", id)
            .put("mission", analysis.mission)
            .put("projectKind", analysis.project.kind.name)
            .put("model", modelName)
            .put("completed", result.completed)
            .put("stalled", result.stalled)
            .put("createdAtMillis", now)
            .put("recommendedToolPacks", JSONArray(analysis.recommendedToolPacks))
            .put("tasks", taskResults)
            .put("analysis", analysisToJson(analysis))
            .put("session", sessionToJson(result.session))

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

    fun loadSession(entry: MissionHistoryEntry): MissionSession? {
        val root = runCatching { read(entry) }.getOrNull() ?: return null
        val analysisJson = root.optJSONObject("analysis")
        val sessionJson = root.optJSONObject("session")

        if (analysisJson == null || sessionJson == null) {
            val workspace = workspaceFor(entry) ?: return null
            val analysis = runCatching {
                MissionAnalyzer().analyze(entry.mission, workspace)
            }.getOrNull() ?: return null
            return MissionStateMachine().create(analysis)
        }

        return runCatching {
            val analysis = analysisFromJson(analysisJson)
            val stateById = mutableMapOf<String, JSONObject>()
            val statesArray = sessionJson.optJSONArray("tasks") ?: JSONArray()
            for (index in 0 until statesArray.length()) {
                val state = statesArray.optJSONObject(index) ?: continue
                val id = state.optString("taskId")
                if (id.isNotBlank()) stateById[id] = state
            }

            val states = analysis.plan.tasks.map { task ->
                val json = stateById[task.id]
                if (json == null) {
                    MissionTaskState(task)
                } else {
                    val status = enumOrDefault(
                        json.optString("status"),
                        AgentStatus.QUEUED
                    )
                    MissionTaskState(
                        task = task.copy(status = status),
                        startedAtMillis = json.nullableLong("startedAtMillis"),
                        finishedAtMillis = json.nullableLong("finishedAtMillis"),
                        detail = json.optString("detail").takeIf(String::isNotBlank)
                    )
                }
            }

            MissionSession(
                analysis = analysis,
                tasks = states,
                createdAtMillis = sessionJson.optLong(
                    "createdAtMillis",
                    entry.createdAtMillis
                )
            )
        }.getOrNull()
    }

    private fun analysisToJson(analysis: MissionAnalysis): JSONObject {
        val project = JSONObject()
            .put("kind", analysis.project.kind.name)
            .put("signals", JSONArray(analysis.project.signals))
            .put(
                "suggestedToolPacks",
                JSONArray(analysis.project.suggestedToolPacks)
            )

        val tasks = JSONArray()
        analysis.plan.tasks.forEach { task ->
            tasks.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("role", task.role.name)
                    .put("status", task.status.name)
                    .put("dependsOn", JSONArray(task.dependsOn.toList()))
            )
        }

        val plan = JSONObject()
            .put("id", analysis.plan.id)
            .put("title", analysis.plan.title)
            .put("tasks", tasks)

        return JSONObject()
            .put("mission", analysis.mission)
            .put("project", project)
            .put(
                "capabilities",
                JSONArray(analysis.capabilities.map { it.name })
            )
            .put(
                "recommendedToolPacks",
                JSONArray(analysis.recommendedToolPacks)
            )
            .put("plan", plan)
    }

    private fun sessionToJson(session: MissionSession): JSONObject {
        val tasks = JSONArray()
        session.tasks.forEach { state ->
            tasks.put(
                JSONObject()
                    .put("taskId", state.task.id)
                    .put("status", state.task.status.name)
                    .putNullable("startedAtMillis", state.startedAtMillis)
                    .putNullable("finishedAtMillis", state.finishedAtMillis)
                    .putNullable("detail", state.detail)
            )
        }

        return JSONObject()
            .put("createdAtMillis", session.createdAtMillis)
            .put("tasks", tasks)
    }

    private fun analysisFromJson(root: JSONObject): MissionAnalysis {
        val projectJson = root.getJSONObject("project")
        val project = ProjectProfile(
            kind = enumOrDefault(
                projectJson.optString("kind"),
                ProjectKind.GENERIC
            ),
            signals = projectJson.optJSONArray("signals").stringList(),
            suggestedToolPacks = projectJson
                .optJSONArray("suggestedToolPacks")
                .stringList()
        )

        val capabilities = root.optJSONArray("capabilities")
            .stringList()
            .mapNotNull { raw ->
                runCatching { MissionCapability.valueOf(raw) }.getOrNull()
            }
            .toSet()
            .ifEmpty { setOf(MissionCapability.INSPECT) }

        val planJson = root.getJSONObject("plan")
        val taskArray = planJson.optJSONArray("tasks") ?: JSONArray()
        val tasks = buildList {
            for (index in 0 until taskArray.length()) {
                val task = taskArray.optJSONObject(index) ?: continue
                add(
                    MissionTask(
                        id = task.getString("id"),
                        title = task.getString("title"),
                        role = enumOrDefault(
                            task.optString("role"),
                            AgentRole.CODER
                        ),
                        status = enumOrDefault(
                            task.optString("status"),
                            AgentStatus.QUEUED
                        ),
                        dependsOn = task.optJSONArray("dependsOn")
                            .stringList()
                            .toSet()
                    )
                )
            }
        }

        return MissionAnalysis(
            mission = root.optString("mission"),
            project = project,
            capabilities = capabilities,
            recommendedToolPacks = root
                .optJSONArray("recommendedToolPacks")
                .stringList(),
            plan = MissionPlan(
                id = planJson.optString("id").ifBlank {
                    UUID.randomUUID().toString()
                },
                title = planJson.optString("title"),
                tasks = tasks
            )
        )
    }

    private fun toEntry(file: File, root: JSONObject): MissionHistoryEntry {
        val tasks = root.optJSONArray("tasks") ?: JSONArray()
        var successful = 0
        for (index in 0 until tasks.length()) {
            if (tasks.optJSONObject(index)?.optBoolean("completed") == true) {
                successful++
            }
        }

        return MissionHistoryEntry(
            id = root.optString("id", file.nameWithoutExtension),
            mission = root.optString("mission"),
            projectKind = root.optString("projectKind", "GENERIC"),
            modelName = root.optString("model"),
            completed = root.optBoolean("completed"),
            stalled = root.optBoolean("stalled"),
            createdAtMillis = root.optLong(
                "createdAtMillis",
                file.lastModified()
            ),
            taskCount = tasks.length(),
            successfulTasks = successful,
            file = file
        )
    }

    private fun workspaceFor(entry: MissionHistoryEntry): File? =
        entry.file.parentFile
            ?.parentFile
            ?.parentFile
            ?.takeIf { it.isDirectory }

    companion object {
        private const val SNAPSHOT_VERSION = 2
    }
}

private fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(
    raw: String,
    default: T
): T = runCatching { enumValueOf<T>(raw) }.getOrDefault(default)

private fun JSONObject.putNullable(
    key: String,
    value: Any?
): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.nullableLong(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null
