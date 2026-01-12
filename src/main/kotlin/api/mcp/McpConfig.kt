package api.mcp

/**
 * Transport type for MCP server connection.
 */
enum class McpTransportType {
    /**
     * Server-Sent Events (SSE) transport.
     */
    SSE,

    /**
     * Streamable HTTP transport.
     */
    STREAMABLE_HTTP,

    /**
     * STDIO transport.
     */
    STDIO
}

/**
 * Configuration for a single MCP server.
 */
data class McpServerConfig(
    /**
     * Unique identifier for this MCP server.
     */
    val id: String,
    
    /**
     * Host address of the MCP server (e.g., "http://127.0.0.1" or "https://mcp.example.com").
     * Used when [baseUrl] is not provided.
     */
    val host: String = "",
    
    /**
     * Port number of the MCP server.
     * Used when [baseUrl] is not provided.
     */
    val port: Int = 0,
    
    /**
     * Full base URL for the MCP server (e.g., "https://mcp.api.coingecko.com/mcp").
     * If provided, this takes precedence over [host] and [port].
     * For SSE transport, the SDK will automatically append "/sse" to this URL.
     */
    val baseUrl: String? = null,
    
    /**
     * Transport type to use for connecting to the MCP server.
     * Defaults to SSE for backward compatibility.
     */
    val transportType: McpTransportType = McpTransportType.SSE,
    
    /**
     * Request timeout in milliseconds.
     */
    val requestTimeoutMillis: Long = 15_000L
) {
    /**
     * Constructs the base URL for the SSE endpoint.
     * The SDK will automatically append "/sse" to this URL.
     * If [baseUrl] is provided, it's used directly.
     * Otherwise, constructs URL from [host] and [port].
     */
    fun getSseUrl(): String {
        return baseUrl ?: "$host:$port"
    }
    
    /**
     * Constructs the base URL for the connection.
     * If [baseUrl] is provided, it's used directly.
     * Otherwise, constructs URL from [host] and [port].
     * This method is transport-agnostic and can be used for both SSE and StreamableHttp.
     */
    fun getConnectionUrl(): String {
        return baseUrl ?: "$host:$port"
    }
}

/**
 * Configuration for multiple MCP servers.
 *
 * This class holds the configuration for all MCP servers that the application
 * should connect to. Servers are defined in code here.
 */
object McpConfig {
    val servers: List<McpServerConfig> = listOf(
        McpServerConfig(
            id = "default",
            baseUrl = "http://127.0.0.1:3008/mcp",
            transportType = McpTransportType.SSE,
            requestTimeoutMillis = 15_000L
        ),
        // Example configuration for git MCP server via STDIO
        McpServerConfig(
            id = "git",
            host = "uvx mcp-server-git",
            transportType = McpTransportType.STDIO,
            requestTimeoutMillis = 15_000L
        )
        // McpServerConfig(
        //     id = "streamable-http-server",
        //     baseUrl = "http://127.0.0.1:8000/mcp",
        //     transportType = McpTransportType.STREAMABLE_HTTP,
        //     requestTimeoutMillis = 15_000L
        // )
    )

    /**
     * Gets a server configuration by ID.
     * @return The server config if found, null otherwise.
     */
    fun getServerById(id: String): McpServerConfig? {
        return servers.find { it.id == id }
    }

    /**
     * Checks if any servers are configured.
     */
    fun hasServers(): Boolean = servers.isNotEmpty()
}

