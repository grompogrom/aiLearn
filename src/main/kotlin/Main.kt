import core.config.ConfigLoader
import core.conversation.ConversationManager
import core.utils.use
import api.provider.ProviderFactory
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
    
    // Create LLM provider (currently Perplexity, but can be easily switched)
    ProviderFactory.createFromConfig(config).use { provider ->
        // Create conversation manager with provider and config
        val conversationManager = ConversationManager(provider, config)
        
        // Create and start frontend (currently CLI, but can be easily replaced)
        val frontend = CliFrontend(config)
        frontend.start(conversationManager)
    }
}
