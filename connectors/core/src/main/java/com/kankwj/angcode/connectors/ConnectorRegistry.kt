package com.kankwj.angcode.connectors

enum class ConnectorKind { MCP, GITHUB, SSH, ANDROID }
enum class ConnectorState { AVAILABLE, DISCONNECTED, CONNECTED }

data class ConnectorDescriptor(
    val id: String,
    val name: String,
    val kind: ConnectorKind,
    val state: ConnectorState,
    val description: String
)

class ConnectorRegistry {
    fun defaults(): List<ConnectorDescriptor> = listOf(
        ConnectorDescriptor(
            "mcp.local", "MCP local", ConnectorKind.MCP, ConnectorState.AVAILABLE,
            "Servidores de herramientas locales y remotos."
        ),
        ConnectorDescriptor(
            "lightpanda", "Lightpanda", ConnectorKind.MCP, ConnectorState.DISCONNECTED,
            "Navegador headless para agentes mediante MCP/CDP dentro del sandbox Linux."
        ),
        ConnectorDescriptor(
            "github", "GitHub", ConnectorKind.GITHUB, ConnectorState.DISCONNECTED,
            "Repositorios, ramas, cambios y artefactos."
        ),
        ConnectorDescriptor(
            "ssh", "SSH", ConnectorKind.SSH, ConnectorState.DISCONNECTED,
            "Delegación opcional a otra máquina."
        ),
        ConnectorDescriptor(
            "android.bridge", "Android Bridge", ConnectorKind.ANDROID, ConnectorState.AVAILABLE,
            "Puente para APIs Android y futura integración Shizuku."
        )
    )
}
