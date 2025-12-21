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
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(McpStreamableHttpClient::class.java)

/**
 * Infrastructure client for communicating with an MCP server over Streamable HTTP.
 *
 * Responsibilities:
 * - Establish StreamableHttp connection using server configuration
 * - Surface results as domain-level [McpResult] without throwing unchecked exceptions
 */
class McpStreamableHttpClient(
    private val serverConfig: McpServerConfig
) : McpClient {

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
    private var transport: StreamableHttpClientTransport? = null
    private var mcpClient: Client? = null

    /**
     * Opens a StreamableHttp connection to the MCP server and waits for a tools-list response.
     */
    override suspend fun listTools(): McpResult<List<McpToolInfo>> {
        logger.debug("Listing tools from MCP server (StreamableHttp): ${serverConfig.id}")
        return try {
            ensureConnection()
            val tools = mcpClient!!.listTools()
            val toolList = tools.tools.map { 
                McpToolInfo(
                    it.name, 
                    it.description, 
                    it.inputSchema.toString()
                )
            }
            logger.info("Successfully retrieved ${toolList.size} tools from MCP server: ${serverConfig.id}")
            McpResult.Success(toolList)
        } catch (e: TimeoutCancellationException) {
            logger.warn("Timeout while listing tools from MCP server: ${serverConfig.id}", e)
            McpResult.Error(
                McpError.Timeout(
                    "Timed out while waiting for MCP tools response: ${e.message ?: "timeout"}"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to list tools from MCP server: ${serverConfig.id}", e)
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
    override suspend fun callTool(toolName: String, arguments: String): McpResult<String> {
        logger.debug("Calling tool '$toolName' on MCP server (StreamableHttp): ${serverConfig.id}")
        return try {
            ensureConnection()
            val json = Json { ignoreUnknownKeys = true }
            val args = json.parseToJsonElement(arguments).jsonObject
            logger.trace("Tool arguments parsed: $arguments")
            
            val result = mcpClient!!.callTool(toolName, args)
            
            // Extract result content first - this is the primary source of information
            // The MCP SDK CallToolResult has content and error properties
            val resultString = buildString {
                if (result.content.isNotEmpty()) {
                    result.content.forEachIndexed { index, contentItem ->
                        // Try to extract text content properly
                        val contentText = when {
                            contentItem::class.java.simpleName.contains("Text") -> {
                                try {
                                    val textField = contentItem::class.java.getDeclaredField("text")
                                    textField.isAccessible = true
                                    textField.get(contentItem)?.toString() ?: contentItem.toString()
                                } catch (e: Exception) {
                                    contentItem.toString()
                                }
                            }
                            else -> contentItem.toString()
                        }
                        append(contentText)
                        if (index < result.content.size - 1) {
                            append("\n")
                        }
                    }
                }
            }
            
            // Check for error property only if content is empty
            // Don't check toString() for errors as it may contain "error" in class names
            if (resultString.isBlank()) {
                val errorMessage = try {
                    val errorField = result::class.java.getDeclaredField("error")
                    errorField.isAccessible = true
                    val errorValue = errorField.get(result)
                    if (errorValue != null) {
                        // Try to extract error message from error object
                        try {
                            val errorMsgField = errorValue::class.java.getDeclaredField("message")
                            errorMsgField.isAccessible = true
                            errorMsgField.get(errorValue)?.toString() ?: errorValue.toString()
                        } catch (e: Exception) {
                            errorValue.toString()
                        }
                    } else null
                } catch (e: NoSuchFieldException) {
                    null
                } catch (e: Exception) {
                    null
                }
                
                if (errorMessage != null) {
                    logger.warn("Tool '$toolName' execution returned error on server ${serverConfig.id}: $errorMessage")
                    return McpResult.Error(
                        McpError.ServerError("Tool execution error: $errorMessage")
                    )
                } else {
                    // No error found, but also no content - this might be a valid empty result
                    logger.debug("Tool '$toolName' returned empty result (no error)")
                    return McpResult.Success("")
                }
            }
            
            // We have content - return success
            logger.debug("Tool '$toolName' executed successfully, result length: ${resultString.length}")
            McpResult.Success(resultString)
        } catch (e: TimeoutCancellationException) {
            logger.warn("Timeout while calling tool '$toolName' on MCP server: ${serverConfig.id}", e)
            McpResult.Error(
                McpError.Timeout(
                    "Timed out while calling MCP tool: ${e.message ?: "timeout"}"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to call tool '$toolName' on MCP server: ${serverConfig.id}", e)
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
            logger.debug("Establishing StreamableHttp connection to MCP server: ${serverConfig.id} at ${serverConfig.getConnectionUrl()}")
            transport = StreamableHttpClientTransport(
                client = client,
                url = serverConfig.getConnectionUrl(),
            )
            
            mcpClient = Client(
                clientInfo = Implementation(
                    name = serverConfig.id,
                    version = "1.0.0",
                )
            )
            
            mcpClient!!.connect(transport!!)
            logger.info("Successfully connected to MCP server via StreamableHttp: ${serverConfig.id}")
        } else {
            logger.trace("Connection to MCP server already established: ${serverConfig.id}")
        }
    }

    override fun close() {
        logger.debug("Closing MCP StreamableHttp client: ${serverConfig.id}")
        mcpClient = null
        transport = null
        client.close()
        logger.info("MCP StreamableHttp client closed: ${serverConfig.id}")
    }
}

