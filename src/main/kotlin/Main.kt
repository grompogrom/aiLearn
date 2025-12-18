import core.config.ConfigLoader
import core.conversation.ConversationManager
import core.memory.MemoryStoreFactory
import core.reminder.ReminderChecker
import core.utils.use
import api.provider.ProviderFactory
import api.mcp.McpClient
import api.mcp.McpClientsManager
import api.mcp.McpConfig
import api.mcp.McpSseClient
import api.mcp.McpServiceImpl
import api.mcp.McpStreamableHttpClient
import api.mcp.McpTransportType
import core.mcp.McpService
import frontend.cli.CliFrontend
import kotlinx.coroutines.runBlocking

/**
 * Application entry point.
 * Initializes configuration, creates LLM provider, conversation manager, and frontend,
 * then starts the application.
 */
fun main() = runBlocking {
    // Load configuration from environment variables, config file, or BuildConfig
    val config = ConfigLoader.load()
    
    // Validate API key
    if (config.apiKey.isBlank()) {
        System.err.println("Error: API key is not configured.")
        System.err.println("Please set AILEARN_API_KEY environment variable,")
        System.err.println("or create ailearn.config.properties file with api.key=your_key,")
        System.err.println("or set perplexityApiKey in gradle.properties for build-time injection.")
        return@runBlocking
    }
    
    // Create MCP clients for all configured servers
    // Support mixed transport types (SSE and StreamableHttp) in a single session
    val mcpClients: List<McpClient> = McpConfig.servers.map { serverConfig ->
        when (serverConfig.transportType) {
            McpTransportType.SSE -> McpSseClient(serverConfig)
            McpTransportType.STREAMABLE_HTTP -> McpStreamableHttpClient(serverConfig)
        }
    }
    
    // Use all MCP clients and create service
    McpClientsManager(mcpClients).use {
        val mcpService: McpService = McpServiceImpl(mcpClients)

        // Create LLM provider (currently Perplexity, but can be easily switched)
        ProviderFactory.createFromConfig(config).use { provider ->
            // Create memory store for conversation persistence
            MemoryStoreFactory.create(config).use { memoryStore ->
                // Create temporary frontend to get callbacks
                val tempFrontend = CliFrontend(config, mcpService, null)
                val summarizationCallback = tempFrontend.createSummarizationCallback()
                val reminderCheckCallback = tempFrontend.createReminderCheckCallback()

                // Create conversation manager with provider, config, summarization callback, memory store, and MCP service
                val conversationManager = ConversationManager(
                    provider,
                    config,
                    summarizationCallback,
                    memoryStore,
                    mcpService
                )

                // Initialize conversation manager (loads history if available)
                conversationManager.initialize()

                // Create reminder checker (disabled by default, starts only with /reminder command)
                val reminderChecker = ReminderChecker(
                    conversationManager,
                    mcpService,
                    reminderCheckCallback
                )
                
                // Create final frontend with reminder checker reference
                val frontend = CliFrontend(config, mcpService, reminderChecker)

                // Start frontend with conversation manager
                frontend.start(conversationManager)
            }
        }
    }
}
