package com.kankwj.angcode.agents

data class ModelRequest(
    val system: String,
    val user: String,
    val tools: List<String> = emptyList(),
    val maxOutputTokens: Int = 1_024
)

data class ModelResponse(
    val text: String,
    val requestedTool: String? = null,
    val toolArguments: Map<String, String> = emptyMap()
)

fun interface ModelGateway {
    fun complete(request: ModelRequest): ModelResponse
}
