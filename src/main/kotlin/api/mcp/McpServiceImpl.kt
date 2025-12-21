package api.mcp

import core.mcp.McpError
import core.mcp.McpResult
import core.mcp.McpService
import core.mcp.McpToolInfo
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(McpClientsManager::class.java)

/**
 * Helper class to manage multiple MCP clients as a single AutoCloseable resource.
 */
class McpClientsManager(
    private val clients: List<McpClient>
) : AutoCloseable {
    override fun close() {
        logger.debug("Closing ${clients.size} MCP clients")
        clients.forEach { it.close() }
        logger.info("All MCP clients closed")
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

    private val logger = LoggerFactory.getLogger(McpServiceImpl::class.java)

    override suspend fun getAvailableTools(): McpResult<List<McpToolInfo>> {
        logger.debug("Getting available tools from ${clients.size} MCP servers")
        if (clients.isEmpty()) {
            logger.warn("No MCP servers configured")
            return McpResult.Error(
                McpError.NotConfigured("No MCP servers configured")
            )
        }

        val allTools = mutableListOf<McpToolInfo>()
        val errors = mutableListOf<McpError>()

        // Collect tools from all servers
        clients.forEachIndexed { index, client ->
            logger.debug("Querying tools from server ${index + 1}/${clients.size}")
            when (val result = client.listTools()) {
                is McpResult.Success -> {
                    logger.debug("Retrieved ${result.value.size} tools from server ${index + 1}")
                    allTools.addAll(result.value)
                }
                is McpResult.Error -> {
                    logger.warn("Failed to retrieve tools from server ${index + 1}: ${result.error}")
                    errors.add(result.error)
                }
            }
        }

        // If we got at least some tools, return them (even if some servers failed)
        return if (allTools.isNotEmpty()) {
            logger.info("Successfully retrieved ${allTools.size} tools total (${errors.size} server(s) failed)")
            McpResult.Success(allTools)
        } else if (errors.isNotEmpty()) {
            // If all servers failed, return the first error
            logger.error("All MCP servers failed to provide tools")
            McpResult.Error(errors.first())
        } else {
            logger.info("No tools available from any MCP server")
            McpResult.Success(emptyList())
        }
    }
    
    override suspend fun callTool(toolName: String, arguments: String): McpResult<String> {
        logger.debug("Calling tool '$toolName' across ${clients.size} MCP servers")
        if (clients.isEmpty()) {
            logger.warn("No MCP servers configured for tool call")
            return McpResult.Error(
                McpError.NotConfigured("No MCP servers configured")
            )
        }
        
        // First, find the client(s) that have this tool
        val clientsWithTool = mutableListOf<McpClient>()
        
        for ((index, client) in clients.withIndex()) {
            logger.debug("Checking if server ${index + 1} has tool '$toolName'")
            when (val toolsResult = client.listTools()) {
                is McpResult.Success -> {
                    // Check if this client has the requested tool
                    if (toolsResult.value.any { it.name == toolName }) {
                        logger.debug("Server ${index + 1} has tool '$toolName'")
                        clientsWithTool.add(client)
                    } else {
                        logger.trace("Server ${index + 1} does not have tool '$toolName'")
                    }
                }
                is McpResult.Error -> {
                    // If we can't list tools, we'll skip this client
                    logger.warn("Cannot list tools from server ${index + 1}, skipping: ${toolsResult.error}")
                }
            }
        }
        
        // If no client has this tool, return error
        if (clientsWithTool.isEmpty()) {
            logger.error("Tool '$toolName' not found in any MCP server")
            return McpResult.Error(
                McpError.ServerError("Tool '$toolName' not found in any MCP server")
            )
        }
        
        logger.info("Found tool '$toolName' in ${clientsWithTool.size} server(s), attempting execution")
        
        // Try calling the tool in clients that have it
        // If tool exists in multiple servers, try them in order
        val errors = mutableListOf<McpError>()
        
        for ((index, client) in clientsWithTool.withIndex()) {
            logger.debug("Attempting tool execution on server ${index + 1}/${clientsWithTool.size}")
            when (val result = client.callTool(toolName, arguments)) {
                is McpResult.Success -> {
                    logger.info("Tool '$toolName' executed successfully on server ${index + 1}")
                    return result
                }
                is McpResult.Error -> {
                    logger.warn("Tool '$toolName' execution failed on server ${index + 1}: ${result.error}")
                    errors.add(result.error)
                }
            }
        }
        
        // All clients with the tool failed
        logger.error("All ${clientsWithTool.size} server(s) with tool '$toolName' failed to execute it")
        return McpResult.Error(
            errors.firstOrNull() 
                ?: McpError.ConnectionFailed("All MCP servers with tool '$toolName' failed to execute it")
        )
    }
}


