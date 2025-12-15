package api.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Minimal JSON-RPC 2.0 style envelope used by MCP over SSE for the
 * "tools/list" style response. This is intentionally small and tolerant.
 */
@Serializable
data class McpSseEnvelope(
    val jsonrpc: String? = null,
    val id: String? = null,
    val result: McpToolsResult? = null,
    val error: McpErrorResponse? = null
)

@Serializable
data class McpToolsResult(
    val tools: List<McpToolDescriptor> = emptyList()
)

@Serializable
data class McpToolDescriptor(
    val name: String,
    val description: String? = null,
    @SerialName("input_schema")
    val inputSchema: JsonElement? = null
)

@Serializable
data class McpErrorResponse(
    val code: Int? = null,
    val message: String? = null,
    val data: JsonElement? = null
)


