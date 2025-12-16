package core.mcp

/**
 * Domain model representing a single MCP tool.
 */
data class McpToolInfo(
    val name: String,
    val description: String? = null,
    /**
     * Optional stringified representation of the input schema (typically JSON Schema).
     * This is kept as a string in the domain to avoid coupling to a specific schema library.
     */
    val inputSchema: String? = null
)

/**
 * Domain-level error type for MCP operations.
 */
sealed class McpError {
    /**
     * MCP is not configured (no host/endpoint specified).
     */
    data class NotConfigured(val message: String) : McpError()

    /**
     * Network or connection failure to the MCP server.
     */
    data class ConnectionFailed(val message: String, val cause: Throwable? = null) : McpError()

    /**
     * The MCP server responded with an error object.
     */
    data class ServerError(val message: String) : McpError()

    /**
     * The response from the MCP server was invalid or could not be parsed.
     */
    data class InvalidResponse(val message: String, val cause: Throwable? = null) : McpError()

    /**
     * The request did not complete in the configured time.
     */
    data class Timeout(val message: String) : McpError()
}

/**
 * Simple result wrapper used by MCP-related APIs to avoid throwing deep inside infrastructure.
 */
sealed class McpResult<out T> {
    data class Success<T>(val value: T) : McpResult<T>()
    data class Error(val error: McpError) : McpResult<Nothing>()
}

/**
 * Application-level service abstraction for interacting with MCP.
 *
 * Kept in the core layer so that frontends can depend on this interface while
 * the concrete implementation lives in the infrastructure/API layer.
 */
interface McpService {
    /**
     * Returns the list of available tools from the MCP server.
     */
    suspend fun getAvailableTools(): McpResult<List<McpToolInfo>>
    
    /**
     * Executes a tool with the given name and arguments.
     * @param toolName The name of the tool to execute
     * @param arguments JSON string containing the tool arguments
     * @return The result of tool execution as a string
     */
    suspend fun callTool(toolName: String, arguments: String): McpResult<String>
}


