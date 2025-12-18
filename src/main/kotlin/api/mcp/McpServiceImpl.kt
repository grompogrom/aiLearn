package api.mcp

import core.mcp.McpError
import core.mcp.McpResult
import core.mcp.McpService
import core.mcp.McpToolInfo

/**
 * Helper class to manage multiple MCP clients as a single AutoCloseable resource.
 */
class McpClientsManager(
    private val clients: List<McpClient>
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
 * Supports mixed transport types (SSE and StreamableHttp) in a single session.
 */
class McpServiceImpl(
    private val clients: List<McpClient>
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
    
    override suspend fun callTool(toolName: String, arguments: String): McpResult<String> {
        if (clients.isEmpty()) {
            return McpResult.Error(
                McpError.NotConfigured("No MCP servers configured")
            )
        }
        
        // First, find the client(s) that have this tool
        val clientsWithTool = mutableListOf<McpClient>()
        
        for (client in clients) {
            when (val toolsResult = client.listTools()) {
                is McpResult.Success -> {
                    // Check if this client has the requested tool
                    if (toolsResult.value.any { it.name == toolName }) {
                        clientsWithTool.add(client)
                    }
                }
                is McpResult.Error -> {
                    // If we can't list tools, we'll skip this client
                    // It might be a connection issue, but we'll try others
                }
            }
        }
        
        // If no client has this tool, return error
        if (clientsWithTool.isEmpty()) {
            return McpResult.Error(
                McpError.ServerError("Tool '$toolName' not found in any MCP server")
            )
        }
        
        // Try calling the tool in clients that have it
        // If tool exists in multiple servers, try them in order
        val errors = mutableListOf<McpError>()
        
        for (client in clientsWithTool) {
            when (val result = client.callTool(toolName, arguments)) {
                is McpResult.Success -> {
                    return result
                }
                is McpResult.Error -> {
                    errors.add(result.error)
                }
            }
        }
        
        // All clients with the tool failed
        return McpResult.Error(
            errors.firstOrNull() 
                ?: McpError.ConnectionFailed("All MCP servers with tool '$toolName' failed to execute it")
        )
    }
}


