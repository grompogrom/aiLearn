package api.mcp

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
     * The SDK will automatically append "/sse" to this URL.
     */
    val baseUrl: String? = null,
    
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
}

/**
 * Configuration for multiple MCP servers.
 * 
 * This class holds the configuration for all MCP servers that the application
 * should connect to. Servers are defined in code here.
 */
object McpConfig {
    /**
     * List of configured MCP servers.
     * Add or modify server configurations here.
     */
    val servers: List<McpServerConfig> = listOf(
        McpServerConfig(
            id = "default",
            baseUrl = "http://127.0.0.1:3002/sse",
            requestTimeoutMillis = 15_000L
        )
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

