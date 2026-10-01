package com.kankwj.angcode.connectors

enum class PrivilegeLevel {
    APP,
    SHIZUKU,
    ROOT
}

enum class AndroidCapability {
    DEVICE_INFO,
    BATTERY,
    CLIPBOARD,
    NOTIFICATIONS,
    SENSORS,
    CAMERA,
    LOCATION,
    PACKAGE_MANAGER,
    ACTIVITY_MANAGER,
    INPUT,
    SCREENSHOT,
    LOGCAT
}

data class BridgeAvailability(
    val backendId: String,
    val privilege: PrivilegeLevel,
    val available: Boolean,
    val capabilities: Set<AndroidCapability>,
    val reason: String? = null
)

interface AndroidBridgeBackend {
    val id: String
    val privilege: PrivilegeLevel
    fun availability(): BridgeAvailability
}

class AndroidBridgeRouter(
    private val backends: List<AndroidBridgeBackend>
) {
    fun bestBackendFor(capability: AndroidCapability): BridgeAvailability? {
        return backends
            .map { it.availability() }
            .filter { it.available && capability in it.capabilities }
            .minByOrNull { it.privilege.ordinal }
    }

    fun snapshot(): List<BridgeAvailability> =
        backends.map { it.availability() }
}
