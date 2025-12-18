package api.mcp

import core.mcp.McpResult
import core.mcp.McpToolInfo

/**
 * Common interface for MCP client implementations.
 * 
 * Allows different transport types (SSE, StreamableHttp) to be used interchangeably
 * while maintaining the same interface for tool listing and execution.
 */
interface McpClient : AutoCloseable {
    /**
     * Returns the list of available tools from the MCP server.
     */
    suspend fun listTools(): McpResult<List<McpToolInfo>>
    
    /**
     * Executes a tool with the given name and arguments.
     * @param toolName The name of the tool to execute
     * @param arguments JSON string containing the tool arguments
     * @return The result of tool execution as a string
     */
    suspend fun callTool(toolName: String, arguments: String): McpResult<String>
}

