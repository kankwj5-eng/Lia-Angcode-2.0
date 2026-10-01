package com.kankwj.angcode.runtime

data class ToolPackManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val requiredPermissions: Set<ToolPermission>,
    val packages: List<String>,
    val exportedTools: List<String>
)
