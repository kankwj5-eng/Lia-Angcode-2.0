package com.kankwj.angcode.automation

import java.util.UUID

data class LocalAutomation(
    val id: String,
    val name: String,
    val mission: String,
    val workspaceName: String,
    val intervalMinutes: Long,
    val requireNetwork: Boolean,
    val requireCharging: Boolean,
    val enabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastRunAtMillis: Long? = null,
    val lastStatus: String? = null,
    val lastDetail: String? = null
)

object AutomationRules {
    const val MIN_INTERVAL_MINUTES = 15L
    const val MAX_INTERVAL_MINUTES = 7L * 24L * 60L
    const val MAX_AUTOMATIONS = 50
    const val MAX_NAME_CHARS = 80
    const val MAX_MISSION_CHARS = 8_000

    private val AUTOMATION_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")
    private val WORKSPACE_NAME = Regex("^[A-Za-z0-9._-]{1,80}$")

    fun newId(): String = UUID.randomUUID().toString()

    fun validate(definition: LocalAutomation): List<String> {
        val errors = mutableListOf<String>()
        if (!AUTOMATION_ID.matches(definition.id)) errors += "id inválido"
        if (definition.name.trim().length !in 1..MAX_NAME_CHARS) {
            errors += "name debe tener 1..$MAX_NAME_CHARS caracteres"
        }
        if (definition.mission.trim().length !in 1..MAX_MISSION_CHARS) {
            errors += "mission debe tener 1..$MAX_MISSION_CHARS caracteres"
        }
        if (
            !WORKSPACE_NAME.matches(definition.workspaceName) ||
            definition.workspaceName == "." ||
            definition.workspaceName == ".."
        ) {
            errors += "workspaceName inválido"
        }
        if (definition.intervalMinutes !in MIN_INTERVAL_MINUTES..MAX_INTERVAL_MINUTES) {
            errors += "intervalMinutes fuera de rango"
        }
        return errors
    }

    fun normalized(definition: LocalAutomation): LocalAutomation =
        definition.copy(
            name = definition.name.trim(),
            mission = definition.mission.trim(),
            workspaceName = definition.workspaceName.trim(),
            intervalMinutes = definition.intervalMinutes.coerceIn(
                MIN_INTERVAL_MINUTES,
                MAX_INTERVAL_MINUTES
            )
        )
}
