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
            val transport = SseClientTransport(
                client = client,
                urlString = serverConfig.getSseUrl(),
            )

            val client = Client(
                clientInfo = Implementation(
                    name = serverConfig.id,
                    version = "1.0.0",
                )
            )

            client.connect(transport)

            // Пример: получить список инструментов и вызвать echo
            val tools = client.listTools()
            McpResult.Success(
                tools.tools.map { McpToolInfo(it.name, it.description, null) }
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

    override fun close() {
        client.close()
    }
}


