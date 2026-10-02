package com.kankwj.angcode.automation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AutomationStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "automations").apply { mkdirs() }
    private val file = File(root, "automations.json")
    private val backup = File(root, "automations.json.bak")
    private val lock = Any()

    fun list(): List<LocalAutomation> = synchronized(lock) {
        readUnlocked().sortedWith(
            compareByDescending<LocalAutomation> { it.enabled }
                .thenBy { it.name.lowercase() }
        )
    }

    fun get(id: String): LocalAutomation? = synchronized(lock) {
        readUnlocked().firstOrNull { it.id == id }
    }

    fun save(definition: LocalAutomation): LocalAutomation = synchronized(lock) {
        val normalized = AutomationRules.normalized(definition)
        val errors = AutomationRules.validate(normalized)
        require(errors.isEmpty()) { errors.joinToString("; ") }

        val current = readUnlocked().toMutableList()
        val index = current.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            current[index] = normalized
        } else {
            require(current.size < AutomationRules.MAX_AUTOMATIONS) {
                "Límite de automatizaciones alcanzado"
            }
            current += normalized
        }
        writeUnlocked(current)
        normalized
    }

    fun setEnabled(id: String, enabled: Boolean): LocalAutomation? = synchronized(lock) {
        val current = readUnlocked().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized null
        val updated = current[index].copy(enabled = enabled)
        current[index] = updated
        writeUnlocked(current)
        updated
    }

    fun markResult(
        id: String,
        status: String,
        detail: String,
        atMillis: Long = System.currentTimeMillis()
    ): LocalAutomation? = synchronized(lock) {
        val current = readUnlocked().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized null
        val updated = current[index].copy(
            lastRunAtMillis = atMillis,
            lastStatus = status.take(40),
            lastDetail = detail.take(2_000)
        )
        current[index] = updated
        writeUnlocked(current)
        updated
    }

    fun remove(id: String): Boolean = synchronized(lock) {
        val current = readUnlocked().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) writeUnlocked(current)
        removed
    }

    private fun readUnlocked(): List<LocalAutomation> {
        val source = when {
            file.isFile -> file
            backup.isFile -> backup
            else -> return emptyList()
        }
        return runCatching {
            val array = JSONArray(source.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    decode(item)?.let(::add)
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun writeUnlocked(items: List<LocalAutomation>) {
        root.mkdirs()
        val array = JSONArray()
        items.forEach { array.put(encode(it)) }

        val temp = File(root, "automations.json.tmp")
        temp.writeText(array.toString(2))
        temp.outputStream().fd.sync()

        if (backup.exists()) backup.delete()
        if (file.exists() && !file.renameTo(backup)) {
            file.copyTo(backup, overwrite = true)
            file.delete()
        }

        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        backup.delete()
    }

    private fun encode(value: LocalAutomation): JSONObject =
        JSONObject()
            .put("id", value.id)
            .put("name", value.name)
            .put("mission", value.mission)
            .put("workspaceName", value.workspaceName)
            .put("intervalMinutes", value.intervalMinutes)
            .put("requireNetwork", value.requireNetwork)
            .put("requireCharging", value.requireCharging)
            .put("enabled", value.enabled)
            .put("createdAtMillis", value.createdAtMillis)
            .put("lastRunAtMillis", value.lastRunAtMillis ?: JSONObject.NULL)
            .put("lastStatus", value.lastStatus ?: JSONObject.NULL)
            .put("lastDetail", value.lastDetail ?: JSONObject.NULL)

    private fun decode(item: JSONObject): LocalAutomation? =
        runCatching {
            val definition = LocalAutomation(
                id = item.getString("id"),
                name = item.getString("name"),
                mission = item.getString("mission"),
                workspaceName = item.getString("workspaceName"),
                intervalMinutes = item.getLong("intervalMinutes"),
                requireNetwork = item.optBoolean("requireNetwork", false),
                requireCharging = item.optBoolean("requireCharging", false),
                enabled = item.optBoolean("enabled", true),
                createdAtMillis = item.optLong("createdAtMillis", 0L),
                lastRunAtMillis = item.optLongOrNull("lastRunAtMillis"),
                lastStatus = item.optStringOrNull("lastStatus"),
                lastDetail = item.optStringOrNull("lastDetail")
            )
            if (AutomationRules.validate(definition).isEmpty()) definition else null
        }.getOrNull()
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
