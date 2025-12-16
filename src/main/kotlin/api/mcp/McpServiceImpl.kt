package api.mcp

import core.mcp.McpError
import core.mcp.McpResult
import core.mcp.McpService
import core.mcp.McpToolInfo

/**
 * Helper class to manage multiple MCP clients as a single AutoCloseable resource.
 */
class McpClientsManager(
    private val clients: List<McpSseClient>
) : AutoCloseable {
    override fun close() {
        clients.forEach { it.close() }
    }
}

/**
 * Application-level service implementation that coordinates access to multiple MCP servers.
 *
 * Aggregates tools from all configured MCP servers. If any server fails, it continues
 * collecting tools from other servers and reports errors separately.
 */
class McpServiceImpl(
    private val clients: List<McpSseClient>
) : McpService {

    override suspend fun getAvailableTools(): McpResult<List<McpToolInfo>> {
        if (clients.isEmpty()) {
            return McpResult.Error(
                McpError.NotConfigured("No MCP servers configured")
            )
        }

        val allTools = mutableListOf<McpToolInfo>()
        val errors = mutableListOf<McpError>()

        // Collect tools from all servers
        clients.forEach { client ->
            when (val result = client.listTools()) {
                is McpResult.Success -> {
                    allTools.addAll(result.value)
                }
                is McpResult.Error -> {
                    errors.add(result.error)
                }
            }
        }

        // If we got at least some tools, return them (even if some servers failed)
        return if (allTools.isNotEmpty()) {
            McpResult.Success(allTools)
        } else if (errors.isNotEmpty()) {
            // If all servers failed, return the first error
            McpResult.Error(errors.first())
        } else {
            McpResult.Success(emptyList())
        }
    }
}


