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
import api.ollama.OllamaClient
import core.mcp.McpService
import core.rag.IndexingService
import core.rag.RagQueryService
import core.rag.LlmReranker
import core.rag.OllamaReranker
import core.rag.ProviderReranker
import frontend.cli.CliFrontend
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Main")

/**
 * Application entry point.
 * Initializes configuration, creates LLM provider, conversation manager, and frontend,
 * then starts the application.
 */
fun main() = runBlocking {
    logger.info("Starting aiLearn application")
    
    try {
        // Load configuration from environment variables, config file, or BuildConfig
        logger.debug("Loading application configuration")
        val config = ConfigLoader.load()
        logger.info("Configuration loaded successfully")
        
        // Validate API key
        if (config.apiKey.isBlank()) {
            logger.error("API key is not configured")
            System.err.println("Error: API key is not configured.")
            System.err.println("Please set AILEARN_API_KEY environment variable,")
            System.err.println("or create ailearn.config.properties file with api.key=your_key,")
            System.err.println("or set perplexityApiKey in gradle.properties for build-time injection.")
            return@runBlocking
        }
        logger.debug("API key validation passed")
    
        // Create MCP clients for all configured servers
        // Support mixed transport types (SSE and StreamableHttp) in a single session
        logger.info("Initializing MCP clients (${McpConfig.servers.size} servers configured)")
        val mcpClients: List<McpClient> = McpConfig.servers.map { serverConfig ->
            logger.debug("Creating MCP client for server: ${serverConfig.id}, transport: ${serverConfig.transportType}")
            when (serverConfig.transportType) {
                McpTransportType.SSE -> McpSseClient(serverConfig)
                McpTransportType.STREAMABLE_HTTP -> McpStreamableHttpClient(serverConfig)
            }
        }
        logger.info("MCP clients created: ${mcpClients.size}")
        
        // Create Ollama client for RAG indexing
        logger.debug("Creating Ollama client for RAG indexing")
        OllamaClient().use { ollamaClient ->
            logger.info("Ollama client created")
            
            // Create RAG indexing service
            logger.debug("Creating RAG indexing service")
            val indexingService = IndexingService(ollamaClient)
            
            // Set up progress callback for indexing
            indexingService.progressCallback = { message ->
                println(message)
            }
            logger.info("RAG indexing service created")
            
            // Use all MCP clients and create service
            McpClientsManager(mcpClients).use {
                val mcpService: McpService = McpServiceImpl(mcpClients)
                logger.debug("MCP service created")

                // Create LLM provider (currently Perplexity, but can be easily switched)
                logger.debug("Creating LLM provider")
                ProviderFactory.createFromConfig(config).use { provider ->
                    logger.info("LLM provider created: ${provider::class.simpleName}")
                    
                    // Create RAG re-ranker if enabled
                    val reranker: LlmReranker? = if (config.ragReranking) {
                        logger.info("RAG re-ranking enabled with provider: ${config.ragRerankingProvider}")
                        when (config.ragRerankingProvider.lowercase()) {
                            "ollama" -> {
                                logger.debug("Creating Ollama re-ranker with model: ${config.ragRerankModel}")
                                OllamaReranker(ollamaClient, config.ragRerankModel)
                            }
                            "llm" -> {
                                logger.debug("Creating LlmProvider re-ranker")
                                ProviderReranker(provider, config)
                            }
                            else -> {
                                logger.warn("Invalid ragRerankingProvider: ${config.ragRerankingProvider}, disabling re-ranking")
                                null
                            }
                        }
                    } else {
                        logger.debug("RAG re-ranking disabled")
                        null
                    }
                    
                    // Create RAG query service
                    logger.debug("Creating RAG query service")
                    val ragQueryService = RagQueryService(ollamaClient, provider, config, reranker = reranker)
                    logger.info("RAG query service created")
                    
                    // Create memory store for conversation persistence (only if useMessageHistory is enabled)
                    if (config.useMessageHistory) {
                        logger.debug("Creating memory store (type: ${config.memoryStoreType})")
                        MemoryStoreFactory.create(config).use { memoryStore ->
                            logger.info("Memory store created: ${memoryStore::class.simpleName}")
                            
                            // Create temporary frontend to get callbacks
                            val tempFrontend = CliFrontend(config, mcpService, null, null, null)
                            val summarizationCallback = tempFrontend.createSummarizationCallback()
                            val reminderCheckCallback = tempFrontend.createReminderCheckCallback()

                            // Create conversation manager with provider, config, summarization callback, memory store, and MCP service
                            logger.debug("Creating conversation manager")
                            val conversationManager = ConversationManager(
                                provider,
                                config,
                                summarizationCallback,
                                memoryStore,
                                mcpService
                            )

                            // Initialize conversation manager (loads history if available)
                            logger.debug("Initializing conversation manager")
                            conversationManager.initialize()
                            logger.info("Conversation manager initialized")

                            // Create reminder checker (disabled by default, starts only with /reminder command)
                            logger.debug("Creating reminder checker")
                            val reminderChecker = ReminderChecker(
                                conversationManager,
                                mcpService,
                                reminderCheckCallback
                            )
                            
                            // Create final frontend with reminder checker, indexing service, and RAG query service references
                            logger.debug("Creating CLI frontend")
                            val frontend = CliFrontend(config, mcpService, reminderChecker, indexingService, ragQueryService)

                            // Start frontend with conversation manager
                            logger.info("Starting application frontend")
                            frontend.start(conversationManager)
                            logger.info("Application frontend stopped")
                        }
                    } else {
                        logger.info("Memory store disabled (useMessageHistory = false)")
                        
                        // Create temporary frontend to get callbacks
                        val tempFrontend = CliFrontend(config, mcpService, null, null, null)
                        val summarizationCallback = tempFrontend.createSummarizationCallback()
                        val reminderCheckCallback = tempFrontend.createReminderCheckCallback()

                        // Create conversation manager without memory store
                        logger.debug("Creating conversation manager without memory store")
                        val conversationManager = ConversationManager(
                            provider,
                            config,
                            summarizationCallback,
                            null,
                            mcpService
                        )

                        // Initialize conversation manager
                        logger.debug("Initializing conversation manager")
                        conversationManager.initialize()
                        logger.info("Conversation manager initialized")

                        // Create reminder checker (disabled by default, starts only with /reminder command)
                        logger.debug("Creating reminder checker")
                        val reminderChecker = ReminderChecker(
                            conversationManager,
                            mcpService,
                            reminderCheckCallback
                        )
                        
                        // Create final frontend with reminder checker, indexing service, and RAG query service references
                        logger.debug("Creating CLI frontend")
                        val frontend = CliFrontend(config, mcpService, reminderChecker, indexingService, ragQueryService)

                        // Start frontend with conversation manager
                        logger.info("Starting application frontend")
                        frontend.start(conversationManager)
                        logger.info("Application frontend stopped")
                    }
                }
            }
        }
    } catch (e: Exception) {
        logger.error("Fatal error during application startup", e)
        throw e
    } finally {
        logger.info("Application shutting down")
    }
}
