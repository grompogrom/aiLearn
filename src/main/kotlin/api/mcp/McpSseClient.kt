package api.mcp

import core.mcp.McpError
import core.mcp.McpResult
import core.mcp.McpToolInfo
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Infrastructure client for communicating with an MCP server over HTTP SSE.
 *
 * Responsibilities:
 * - Establish SSE connection using server configuration
 * - Read events and delegate parsing to [McpSseResponseParser]
 * - Surface results as domain-level [McpResult] without throwing unchecked exceptions
 */
class McpSseClient(
    private val serverConfig: McpServerConfig
) : AutoCloseable {

    private val client = HttpClient(CIO) {
        install(SSE)
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = serverConfig.requestTimeoutMillis
        }
    }
    
    // Store transport and client for reuse
    private var transport: SseClientTransport? = null
    private var mcpClient: Client? = null

    /**
     * Opens an SSE stream to the MCP server and waits for a tools-list response.
     *
     * The concrete wire protocol is intentionally minimal here:
     * - We connect to the configured SSE endpoint.
     * - We listen for the first event that can be parsed as a tools-list response.
     * - Any protocol-specific triggering of "tools/list" on the server side is
     *   expected to be handled by the MCP server configuration.
     */
    suspend fun listTools(): McpResult<List<McpToolInfo>> {
        return try {
            ensureConnection()
            val tools = mcpClient!!.listTools()
            McpResult.Success(
                tools.tools.map { 
                    McpToolInfo(
                        it.name, 
                        it.description, 
                        it.inputSchema?.toString()
                    )
                }
            )
        } catch (e: TimeoutCancellationException) {
            McpResult.Error(
                McpError.Timeout(
                    "Timed out while waiting for MCP tools response: ${e.message ?: "timeout"}"
                )
            )
        } catch (e: Exception) {
            McpResult.Error(
                McpError.ConnectionFailed(
                    message = "Failed to connect to MCP server: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    /**
     * Executes a tool via MCP.
     */
    suspend fun callTool(toolName: String, arguments: String): McpResult<String> {
        return try {
            ensureConnection()
            val json = Json { ignoreUnknownKeys = true }
            val args = json.parseToJsonElement(arguments).jsonObject
            
            val result = mcpClient!!.callTool(toolName, args)
            
            // Extract result content - convert result to string representation
            // The MCP SDK CallToolResult has content and error properties
            val resultString = buildString {
                if (result.content.isNotEmpty()) {
                    result.content.forEachIndexed { index, contentItem ->
                        append(contentItem.toString())
                        if (index < result.content.size - 1) {
                            append("\n")
                        }
                    }
                }
            }
            
            // If no content, check for error
            if (resultString.isBlank()) {
                // Try to get error message from result
                val errorMsg = try {
                    result.toString()
                } catch (e: Exception) {
                    "Tool execution returned empty result"
                }
                if (errorMsg.contains("error", ignoreCase = true)) {
                    McpResult.Error(
                        McpError.ServerError("Tool execution error: $errorMsg")
                    )
                } else {
                    McpResult.Error(
                        McpError.InvalidResponse("Tool execution returned empty result")
                    )
                }
            } else {
                McpResult.Success(resultString)
            }
        } catch (e: TimeoutCancellationException) {
            McpResult.Error(
                McpError.Timeout(
                    "Timed out while calling MCP tool: ${e.message ?: "timeout"}"
                )
            )
        } catch (e: Exception) {
            McpResult.Error(
                McpError.ConnectionFailed(
                    message = "Failed to call MCP tool '$toolName': ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    private suspend fun ensureConnection() {
        if (transport == null || mcpClient == null) {
            transport = SseClientTransport(
                client = client,
                urlString = serverConfig.getSseUrl(),
            )
            
            mcpClient = Client(
                clientInfo = Implementation(
                    name = serverConfig.id,
                    version = "1.0.0",
                )
            )
            
            mcpClient!!.connect(transport!!)
        }
    }

    override fun close() {
        mcpClient = null
        transport = null
        client.close()
    }
}


