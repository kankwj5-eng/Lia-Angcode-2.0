package com.kankwj.angcode.connectors

object GitHubRefs {
    private val BRANCH = Regex("^[A-Za-z0-9][A-Za-z0-9._/-]{0,199}$")

    fun validBranch(value: String): Boolean =
        BRANCH.matches(value) &&
            !value.contains("..") &&
            !value.contains("//") &&
            !value.contains("@{") &&
            !value.endsWith("/") &&
            !value.endsWith(".") &&
            !value.endsWith(".lock") &&
            !value.startsWith("-")
}

object GitHubPaths {
    private val SEGMENT = Regex("^[A-Za-z0-9._@+ -]{1,200}$")

    fun validRepositoryPath(value: String): Boolean {
        if (value.isBlank() || value.length > 1_000) return false
        if (value.startsWith("/") || value.endsWith("/")) return false
        if ('\\' in value || value.contains("//")) return false

        val parts = value.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return false
        return parts.all(SEGMENT::matches)
    }
}
