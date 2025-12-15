package api.mcp

import core.mcp.McpResult
import core.mcp.McpService
import core.mcp.McpToolInfo

/**
 * Application-level service implementation that coordinates access to MCP.
 *
 * Keeps only orchestration logic, delegating transport details to [McpSseClient].
 */
class McpServiceImpl(
    private val client: McpSseClient
) : McpService {

    override suspend fun getAvailableTools(): McpResult<List<McpToolInfo>> {
        return client.listTools()
    }
}


