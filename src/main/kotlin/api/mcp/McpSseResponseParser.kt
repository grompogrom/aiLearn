package api.mcp

import core.mcp.McpError
import core.mcp.McpResult
import core.mcp.McpToolInfo
import kotlinx.serialization.json.Json

/**
 * Parses raw JSON payloads coming from the MCP SSE stream into domain models.
 *
 * Isolated for easier unit testing.
 */
object McpSseResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Parses a single SSE event data payload into a list of tools.
     *
     * Expects a JSON-RPC-like envelope with a `result.tools` array.
     */
    fun parseToolsEventData(data: String): McpResult<List<McpToolInfo>> {
        val envelope = try {
            json.decodeFromString<McpSseEnvelope>(data)
        } catch (e: Exception) {
            return McpResult.Error(
                McpError.InvalidResponse(
                    message = "Failed to parse MCP tools response: ${e.message}",
                    cause = e
                )
            )
        }

        envelope.error?.let { error ->
            val message = buildString {
                append("MCP server returned an error")
                error.code?.let { append(" (code=$it)") }
                if (!error.message.isNullOrBlank()) {
                    append(": ${error.message}")
                }
            }
            return McpResult.Error(McpError.ServerError(message))
        }

        val tools = envelope.result?.tools ?: emptyList()
        if (tools.isEmpty()) {
            return McpResult.Success(emptyList())
        }

        val mapped = tools.map { descriptor ->
            McpToolInfo(
                name = descriptor.name,
                description = descriptor.description,
                inputSchema = descriptor.inputSchema?.toString()
            )
        }

        return McpResult.Success(mapped)
    }
}


