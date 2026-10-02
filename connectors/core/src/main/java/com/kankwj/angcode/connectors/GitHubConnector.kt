package com.kankwj.angcode.connectors

import android.content.Context
import android.util.Base64
import com.kankwj.angcode.runtime.AgentTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.ToolResponse
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

object GitHubNames {
    private val OWNER = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
    private val REPO = Regex("^[A-Za-z0-9._-]{1,100}$")

    fun validOwner(value: String): Boolean =
        OWNER.matches(value) && !value.endsWith("-")

    fun validRepo(value: String): Boolean =
        REPO.matches(value) && value != "." && value != ".."
}

data class GitHubUser(
    val login: String,
    val name: String?,
    val htmlUrl: String?
)

private data class GitHubHttpResponse(
    val status: Int,
    val body: String
)

class GitHubApiClient(
    context: Context,
    private val tokenStore: GitHubTokenStore = GitHubTokenStore(context)
) {
    fun connected(): Boolean = tokenStore.hasToken()

    fun currentUser(): GitHubUser {
        val root = JSONObject(requireSuccess(get("/user")))
        return GitHubUser(
            login = root.getString("login"),
            name = root.optString("name").takeIf { it.isNotBlank() && it != "null" },
            htmlUrl = root.optString("html_url").takeIf { it.isNotBlank() }
        )
    }

    fun repository(owner: String, repo: String): String {
        requireRepo(owner, repo)
        val root = JSONObject(requireSuccess(get("/repos/$owner/$repo")))
        return buildString {
            appendLine("full_name=" + root.optString("full_name"))
            appendLine("private=" + root.optBoolean("private"))
            appendLine("default_branch=" + root.optString("default_branch"))
            appendLine("fork=" + root.optBoolean("fork"))
            appendLine("archived=" + root.optBoolean("archived"))
            appendLine("open_issues_count=" + root.optInt("open_issues_count"))
            appendLine("stargazers_count=" + root.optInt("stargazers_count"))
            appendLine("language=" + root.optString("language"))
            appendLine("updated_at=" + root.optString("updated_at"))
            append("html_url=" + root.optString("html_url"))
        }
    }

    fun issues(
        owner: String,
        repo: String,
        state: String,
        limit: Int
    ): String {
        requireRepo(owner, repo)
        val normalizedState = state.takeIf { it in setOf("open", "closed", "all") } ?: "open"
        val body = requireSuccess(
            get(
                "/repos/$owner/$repo/issues",
                mapOf(
                    "state" to normalizedState,
                    "per_page" to limit.coerceIn(1, 100).toString()
                )
            )
        )
        val array = JSONArray(body)
        val out = ArrayList<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.has("pull_request")) continue
            out += buildString {
                append("#" + item.optInt("number"))
                append("\t" + item.optString("state"))
                append("\t" + item.optString("title").replace("\n", " ").take(240))
                append("\t" + item.optString("html_url"))
            }
        }
        return out.joinToString("\n")
    }

    fun pulls(
        owner: String,
        repo: String,
        state: String,
        limit: Int
    ): String {
        requireRepo(owner, repo)
        val normalizedState = state.takeIf { it in setOf("open", "closed", "all") } ?: "open"
        val body = requireSuccess(
            get(
                "/repos/$owner/$repo/pulls",
                mapOf(
                    "state" to normalizedState,
                    "per_page" to limit.coerceIn(1, 100).toString()
                )
            )
        )
        val array = JSONArray(body)
        val out = ArrayList<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            out += buildString {
                append("#" + item.optInt("number"))
                append("\t" + item.optString("state"))
                append("\t" + item.optString("title").replace("\n", " ").take(240))
                append("\t" + item.optJSONObject("head")?.optString("ref").orEmpty())
                append(" -> " + item.optJSONObject("base")?.optString("ref").orEmpty())
                append("\t" + item.optString("html_url"))
            }
        }
        return out.joinToString("\n")
    }

    fun branches(owner: String, repo: String, limit: Int): String {
        requireRepo(owner, repo)
        val body = requireSuccess(
            get(
                "/repos/$owner/$repo/branches",
                mapOf("per_page" to limit.coerceIn(1, 100).toString())
            )
        )
        val array = JSONArray(body)
        val out = ArrayList<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            out += item.optString("name") + "\t" +
                item.optJSONObject("commit")?.optString("sha").orEmpty()
        }
        return out.joinToString("\n")
    }

    fun createBranch(
        owner: String,
        repo: String,
        branch: String,
        fromSha: String
    ): String {
        requireRepo(owner, repo)
        require(GitHubRefs.validBranch(branch)) { "branch inválida" }
        require(fromSha.matches(Regex("^[0-9a-fA-F]{40,64}$"))) {
            "fromSha inválido"
        }

        val body = JSONObject()
            .put("ref", "refs/heads/" + branch)
            .put("sha", fromSha)
            .toString()

        val root = JSONObject(
            requireSuccess(
                request(
                    method = "POST",
                    path = "/repos/$owner/$repo/git/refs",
                    body = body
                )
            )
        )

        return "ref=" + root.optString("ref") +
            "\nsha=" + root.optJSONObject("object")?.optString("sha").orEmpty()
    }

    fun putFile(
        owner: String,
        repo: String,
        path: String,
        branch: String,
        message: String,
        content: String,
        expectedSha: String?
    ): String {
        requireRepo(owner, repo)
        require(GitHubPaths.validRepositoryPath(path)) { "path inválido" }
        require(GitHubRefs.validBranch(branch)) { "branch inválida" }
        require(message.trim().length in 1..500) { "message inválido" }
        require(content.length <= MAX_WRITE_CONTENT_CHARS) {
            "content demasiado grande para github.file.put"
        }
        if (expectedSha != null) {
            require(expectedSha.matches(Regex("^[0-9a-fA-F]{40,64}$"))) {
                "expectedSha inválido"
            }
        }

        val encodedPath = path
            .split('/')
            .joinToString("/") {
                URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
            }

        val payload = JSONObject()
            .put("message", message.trim())
            .put(
                "content",
                Base64.encodeToString(
                    content.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
            )
            .put("branch", branch)

        if (expectedSha != null) {
            payload.put("sha", expectedSha)
        }

        val root = JSONObject(
            requireSuccess(
                request(
                    method = "PUT",
                    path = "/repos/$owner/$repo/contents/$encodedPath",
                    body = payload.toString()
                )
            )
        )

        val commit = root.optJSONObject("commit")
        val file = root.optJSONObject("content")
        return buildString {
            appendLine("commit=" + commit?.optString("sha").orEmpty())
            appendLine("fileSha=" + file?.optString("sha").orEmpty())
            append("html_url=" + file?.optString("html_url").orEmpty())
        }
    }

    fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        bodyText: String?
    ): String {
        requireRepo(owner, repo)
        require(title.trim().length in 1..240) { "title inválido" }
        require(GitHubRefs.validBranch(head)) { "head inválida" }
        require(GitHubRefs.validBranch(base)) { "base inválida" }
        require(bodyText == null || bodyText.length <= 20_000) {
            "body demasiado grande"
        }

        val payload = JSONObject()
            .put("title", title.trim())
            .put("head", head)
            .put("base", base)
        if (!bodyText.isNullOrBlank()) {
            payload.put("body", bodyText)
        }

        val root = JSONObject(
            requireSuccess(
                request(
                    method = "POST",
                    path = "/repos/$owner/$repo/pulls",
                    body = payload.toString()
                )
            )
        )

        return buildString {
            appendLine("number=" + root.optInt("number"))
            appendLine("state=" + root.optString("state"))
            append("html_url=" + root.optString("html_url"))
        }
    }

    fun actions(owner: String, repo: String, limit: Int): String {
        requireRepo(owner, repo)
        val body = requireSuccess(
            get(
                "/repos/$owner/$repo/actions/runs",
                mapOf("per_page" to limit.coerceIn(1, 50).toString())
            )
        )
        val root = JSONObject(body)
        val runs = root.optJSONArray("workflow_runs") ?: JSONArray()
        val out = ArrayList<String>()
        for (index in 0 until runs.length()) {
            val item = runs.optJSONObject(index) ?: continue
            out += buildString {
                append(item.optLong("id"))
                append("\t" + item.optString("name"))
                append("\t" + item.optString("status"))
                append("\t" + item.optString("conclusion"))
                append("\t" + item.optString("head_branch"))
                append("\t" + item.optString("html_url"))
            }
        }
        return out.joinToString("\n")
    }

    private fun requireRepo(owner: String, repo: String) {
        require(GitHubNames.validOwner(owner)) { "owner inválido" }
        require(GitHubNames.validRepo(repo)) { "repo inválido" }
    }

    private fun get(
        path: String,
        query: Map<String, String> = emptyMap()
    ): GitHubHttpResponse =
        request("GET", path, query)

    private fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null
    ): GitHubHttpResponse {
        require(method in setOf("GET", "POST", "PUT")) { "Método GitHub no permitido" }
        require(path.startsWith("/") && !path.startsWith("//"))
        require(body == null || body.length <= MAX_REQUEST_CHARS) {
            "Solicitud GitHub demasiado grande"
        }

        val token = tokenStore.load()
            ?: error("Conector GitHub no configurado")

        val queryText = if (query.isEmpty()) "" else query.entries.joinToString(
            prefix = "?",
            separator = "&"
        ) { (key, value) ->
            URLEncoder.encode(key, Charsets.UTF_8.name()) + "=" +
                URLEncoder.encode(value, Charsets.UTF_8.name())
        }

        val uri = URI("https://api.github.com" + path + queryText)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("Authorization", "Bearer " + token)
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "AngCode/0.2")

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
        }

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.use { input ->
                val buffer = ByteArray(8192)
                val output = java.io.ByteArrayOutputStream()
                while (output.size() <= MAX_RESPONSE_BYTES) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_RESPONSE_BYTES) {
                        error("Respuesta GitHub demasiado grande")
                    }
                }
                output.toString(Charsets.UTF_8.name())
            }.orEmpty()

            GitHubHttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun requireSuccess(response: GitHubHttpResponse): String {
        if (response.status in 200..299) return response.body

        val message = runCatching {
            JSONObject(response.body).optString("message")
        }.getOrNull().orEmpty()

        error(
            "GitHub HTTP " + response.status +
                if (message.isBlank()) "" else ": " + message.take(300)
        )
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_REQUEST_CHARS = 2 * 1024 * 1024
        private const val MAX_WRITE_CONTENT_CHARS = 1 * 1024 * 1024
    }
}

private abstract class GitHubReadTool(
    protected val client: GitHubApiClient
) : AgentTool {
    override val requiredPermissions = setOf(
        ToolPermission.NETWORK,
        ToolPermission.GITHUB_READ
    )

    protected fun ownerRepo(call: ToolCall): Pair<String, String>? {
        val owner = call.arguments["owner"] ?: return null
        val repo = call.arguments["repo"] ?: return null
        if (!GitHubNames.validOwner(owner) || !GitHubNames.validRepo(repo)) return null
        return owner to repo
    }
}

private class GitHubRepoTool(client: GitHubApiClient) : GitHubReadTool(client) {
    override val id = "github.repo"
    override val description = "Consulta metadata de un repositorio GitHub."

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val (owner, repo) = ownerRepo(call)
            ?: return ToolResponse(false, "owner/repo faltante o inválido")
        return runCatching { ToolResponse(true, client.repository(owner, repo)) }
            .getOrElse { ToolResponse(false, it.message ?: "GitHub falló") }
    }
}

private class GitHubIssuesTool(client: GitHubApiClient) : GitHubReadTool(client) {
    override val id = "github.issues"
    override val description = "Lista issues GitHub sin incluir pull requests."

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val (owner, repo) = ownerRepo(call)
            ?: return ToolResponse(false, "owner/repo faltante o inválido")
        val state = call.arguments["state"] ?: "open"
        val limit = call.arguments["limit"]?.toIntOrNull() ?: 30
        return runCatching {
            ToolResponse(true, client.issues(owner, repo, state, limit))
        }.getOrElse { ToolResponse(false, it.message ?: "GitHub falló") }
    }
}

private class GitHubPullsTool(client: GitHubApiClient) : GitHubReadTool(client) {
    override val id = "github.pulls"
    override val description = "Lista pull requests de un repositorio GitHub."

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val (owner, repo) = ownerRepo(call)
            ?: return ToolResponse(false, "owner/repo faltante o inválido")
        val state = call.arguments["state"] ?: "open"
        val limit = call.arguments["limit"]?.toIntOrNull() ?: 30
        return runCatching {
            ToolResponse(true, client.pulls(owner, repo, state, limit))
        }.getOrElse { ToolResponse(false, it.message ?: "GitHub falló") }
    }
}

private class GitHubBranchesTool(client: GitHubApiClient) : GitHubReadTool(client) {
    override val id = "github.branches"
    override val description = "Lista ramas y SHAs de un repositorio GitHub."

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val (owner, repo) = ownerRepo(call)
            ?: return ToolResponse(false, "owner/repo faltante o inválido")
        val limit = call.arguments["limit"]?.toIntOrNull() ?: 50
        return runCatching {
            ToolResponse(true, client.branches(owner, repo, limit))
        }.getOrElse { ToolResponse(false, it.message ?: "GitHub falló") }
    }
}

private class GitHubActionsTool(client: GitHubApiClient) : GitHubReadTool(client) {
    override val id = "github.actions"
    override val description = "Lista ejecuciones recientes de GitHub Actions."

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val (owner, repo) = ownerRepo(call)
            ?: return ToolResponse(false, "owner/repo faltante o inválido")
        val limit = call.arguments["limit"]?.toIntOrNull() ?: 20
        return runCatching {
            ToolResponse(true, client.actions(owner, repo, limit))
        }.getOrElse { ToolResponse(false, it.message ?: "GitHub falló") }
    }
}

private abstract class GitHubWriteTool(
    protected val client: GitHubApiClient
) : AgentTool {
    override val requiredPermissions = setOf(
        ToolPermission.NETWORK,
        ToolPermission.GITHUB_WRITE
    )

    protected fun ownerRepo(call: ToolCall): Pair<String, String>? {
        val owner = call.arguments["owner"] ?: return null
        val repo = call.arguments["repo"] ?: return null
        if (!GitHubNames.validOwner(owner) || !GitHubNames.validRepo(repo)) return null
        return owner to repo
    }
}

private class GitHubCreateBranchTool(client: GitHubApiClient) : GitHubWriteTool(client) {
    override val id = "github.branch.create"
    override val description = "Crea una rama GitHub desde un SHA explícito."

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val (owner, repo) = ownerRepo(call)
            ?: return ToolResponse(false, "owner/repo faltante o inválido")
        val branch = call.arguments["branch"]
            ?: return ToolResponse(false, "Falta branch")
        val fromSha = call.arguments["fromSha"]
            ?: return ToolResponse(false, "Falta fromSha")

        return runCatching {
            ToolResponse(true, client.createBranch(owner, repo, branch, fromSha))
        }.getOrElse { ToolResponse(false, it.message ?: "GitHub falló") }
    }
}

private class GitHubPutFileTool(client: GitHubApiClient) : GitHubWriteTool(client) {
    override val id = "github.file.put"
    override val description =
        "Crea un archivo o actualiza uno existente; para actualizar exige expectedSha."

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val (owner, repo) = ownerRepo(call)
            ?: return ToolResponse(false, "owner/repo faltante o inválido")
        val path = call.arguments["path"]
            ?: return ToolResponse(false, "Falta path")
        val branch = call.arguments["branch"]
            ?: return ToolResponse(false, "Falta branch")
        val message = call.arguments["message"]
            ?: return ToolResponse(false, "Falta message")
        val content = call.arguments["content"]
            ?: return ToolResponse(false, "Falta content")

        return runCatching {
            ToolResponse(
                true,
                client.putFile(
                    owner = owner,
                    repo = repo,
                    path = path,
                    branch = branch,
                    message = message,
                    content = content,
                    expectedSha = call.arguments["expectedSha"]
                )
            )
        }.getOrElse { ToolResponse(false, it.message ?: "GitHub falló") }
    }
}

private class GitHubCreatePullRequestTool(client: GitHubApiClient) : GitHubWriteTool(client) {
    override val id = "github.pr.create"
    override val description = "Abre un pull request dentro del mismo repositorio."

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val (owner, repo) = ownerRepo(call)
            ?: return ToolResponse(false, "owner/repo faltante o inválido")
        val title = call.arguments["title"]
            ?: return ToolResponse(false, "Falta title")
        val head = call.arguments["head"]
            ?: return ToolResponse(false, "Falta head")
        val base = call.arguments["base"]
            ?: return ToolResponse(false, "Falta base")

        return runCatching {
            ToolResponse(
                true,
                client.createPullRequest(
                    owner = owner,
                    repo = repo,
                    title = title,
                    head = head,
                    base = base,
                    bodyText = call.arguments["body"]
                )
            )
        }.getOrElse { ToolResponse(false, it.message ?: "GitHub falló") }
    }
}

fun ToolBroker.registerGitHubTools(context: Context): ToolBroker = apply {
    val client = GitHubApiClient(context.applicationContext)
    register(GitHubRepoTool(client))
    register(GitHubIssuesTool(client))
    register(GitHubPullsTool(client))
    register(GitHubBranchesTool(client))
    register(GitHubActionsTool(client))
    register(GitHubCreateBranchTool(client))
    register(GitHubPutFileTool(client))
    register(GitHubCreatePullRequestTool(client))
}
