package com.kankwj.angcode.runtime

enum class ToolPackRuntime {
    ANDROID_NATIVE,
    PROOT
}

data class UpstreamComponent(
    val name: String,
    val license: String,
    val source: String
)

data class ToolPackManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val requiredPermissions: Set<ToolPermission>,
    val packages: List<String>,
    val exportedTools: List<String>,
    val runtime: ToolPackRuntime = ToolPackRuntime.ANDROID_NATIVE,
    val architectures: List<String> = emptyList(),
    val upstream: UpstreamComponent? = null
)
