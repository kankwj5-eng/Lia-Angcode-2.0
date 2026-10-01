package com.kankwj.angcode.runtime

import android.content.Context
import java.io.File

class ActiveProjectStore(
    context: Context,
    private val workspaceManager: WorkspaceManager = WorkspaceManager(context)
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "angcode-projects",
        Context.MODE_PRIVATE
    )

    fun setActive(workspace: File) {
        val root = workspaceManager.rootDirectory().canonicalFile
        val target = workspace.canonicalFile
        require(target.toPath().startsWith(root.toPath())) {
            "El proyecto activo debe vivir dentro de workspaces"
        }
        require(target.isDirectory) {
            "Workspace no encontrado"
        }
        preferences.edit()
            .putString(KEY_ACTIVE, target.name)
            .apply()
    }

    fun active(): File? {
        val name = preferences.getString(KEY_ACTIVE, null) ?: return null
        val candidate = File(workspaceManager.rootDirectory(), name)
        return candidate.takeIf { it.isDirectory }
    }

    fun resolveActiveOrScratch(): File =
        active() ?: workspaceManager.createWorkspace("_scratch")

    fun clear() {
        preferences.edit().remove(KEY_ACTIVE).apply()
    }

    companion object {
        private const val KEY_ACTIVE = "active_workspace"
    }
}
