package core.config

import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)

/**
 * Loads configuration from multiple sources with priority:
 * 1. Environment variables
 * 2. Config file (if exists)
 * 3. BuildConfig (for API key only)
 * 4. Default values
 */
class ConfigLoader {
    companion object {
        private const val CONFIG_FILE_NAME = "ailearn.config.properties"
        
        fun load(): AppConfig {
            logger.debug("Loading configuration from multiple sources")
            
            val envConfig = EnvironmentConfig()
            logger.debug("Environment config loaded")
            
            val fileConfig = FileConfig.tryLoad(CONFIG_FILE_NAME)
            if (fileConfig != null) {
                logger.info("Configuration file loaded: $CONFIG_FILE_NAME")
            } else {
                logger.debug("Configuration file not found: $CONFIG_FILE_NAME")
            }
            
            val buildConfig = BuildConfigWrapper.tryLoad()
            if (buildConfig != null) {
                logger.debug("BuildConfig loaded (API key available)")
            } else {
                logger.debug("BuildConfig not available")
            }
            
            // Chain: env -> file -> buildConfig -> defaults
            val fileOrBuild = fileConfig ?: buildConfig
            val finalFallback = fileOrBuild ?: DefaultAppConfig()
            
            val config = CompositeConfig(
                primary = envConfig,
                fallback = finalFallback
            )
            
            logger.info("Configuration loaded successfully (API key: ${if (config.apiKey.isNotBlank()) "configured" else "missing"})")
            return config
        }
    }
}

/**
 * Configuration loaded from environment variables.
 */
private class EnvironmentConfig : AppConfig {
    override val apiKey: String
        get() = System.getenv("AILEARN_API_KEY") ?: ""
    
    override val apiUrl: String
        get() = System.getenv("AILEARN_API_URL") ?: DefaultConfig.DEFAULT_API_URL
    
    override val model: String
        get() = System.getenv("AILEARN_MODEL") ?: DefaultConfig.DEFAULT_MODEL
    
    override val maxTokens: Int
        get() = System.getenv("AILEARN_MAX_TOKENS")?.toIntOrNull() ?: DefaultConfig.DEFAULT_MAX_TOKENS
    
    override val temperature: Double
        get() = System.getenv("AILEARN_TEMPERATURE")?.toDoubleOrNull() ?: DefaultConfig.DEFAULT_TEMPERATURE
    
    override val systemPrompt: String
        get() = System.getenv("AILEARN_SYSTEM_PROMPT") ?: DefaultConfig.DEFAULT_SYSTEM_PROMPT
    
    override val dialogEndMarker: String
        get() = System.getenv("AILEARN_DIALOG_END_MARKER") ?: DefaultConfig.DEFAULT_DIALOG_END_MARKER
    
    override val pricePerMillionTokens: Double
        get() = System.getenv("AILEARN_PRICE_PER_MILLION_TOKENS")?.toDoubleOrNull() 
            ?: DefaultConfig.DEFAULT_PRICE_PER_MILLION_TOKENS
    
    override val requestTimeoutMillis: Long
        get() = System.getenv("AILEARN_REQUEST_TIMEOUT_MILLIS")?.toLongOrNull() 
            ?: DefaultConfig.DEFAULT_REQUEST_TIMEOUT_MILLIS
    
    override val useMessageHistory: Boolean
        get() = System.getenv("AILEARN_USE_MESSAGE_HISTORY")?.toBoolean() 
            ?: DefaultConfig.DEFAULT_USE_MESSAGE_HISTORY
    
    override val githubToken: String
        get() = System.getenv("AILEARN_GITHUB_TOKEN") ?: DefaultConfig.DEFAULT_GITHUB_TOKEN
    
    override val enableSummarization: Boolean
        get() = System.getenv("AILEARN_ENABLE_SUMMARIZATION")?.toBoolean() 
            ?: DefaultConfig.DEFAULT_ENABLE_SUMMARIZATION
    
    override val summarizationTokenThreshold: Int
        get() = System.getenv("AILEARN_SUMMARIZATION_TOKEN_THRESHOLD")?.toIntOrNull() 
            ?: DefaultConfig.DEFAULT_SUMMARIZATION_TOKEN_THRESHOLD
    
    override val summarizationModel: String
        get() = System.getenv("AILEARN_SUMMARIZATION_MODEL") ?: DefaultConfig.DEFAULT_SUMMARIZATION_MODEL
    
    override val summarizationMaxTokens: Int
        get() = System.getenv("AILEARN_SUMMARIZATION_MAX_TOKENS")?.toIntOrNull() 
            ?: DefaultConfig.DEFAULT_SUMMARIZATION_MAX_TOKENS
    
    override val summarizationTemperature: Double
        get() = System.getenv("AILEARN_SUMMARIZATION_TEMPERATURE")?.toDoubleOrNull() 
            ?: DefaultConfig.DEFAULT_SUMMARIZATION_TEMPERATURE
    
    override val summarizationSystemPrompt: String
        get() = System.getenv("AILEARN_SUMMARIZATION_SYSTEM_PROMPT") 
            ?: DefaultConfig.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT
    
    override val summarizationPrompt: String
        get() = System.getenv("AILEARN_SUMMARIZATION_PROMPT") 
            ?: DefaultConfig.DEFAULT_SUMMARIZATION_PROMPT
    
    override val memoryStoreType: String
        get() = System.getenv("AILEARN_MEMORY_STORE_TYPE") 
            ?: DefaultConfig.DEFAULT_MEMORY_STORE_TYPE
    
    override val memoryStorePath: String?
        get() = System.getenv("AILEARN_MEMORY_STORE_PATH")?.takeIf { it.isNotEmpty() }
            ?: if (DefaultConfig.DEFAULT_MEMORY_STORE_PATH.isEmpty()) null else DefaultConfig.DEFAULT_MEMORY_STORE_PATH

    override val mcpSseProtocol: String
        get() = System.getenv("AILEARN_MCP_SSE_PROTOCOL") ?: DefaultConfig.DEFAULT_MCP_SSE_PROTOCOL

    override val mcpSseHost: String
        get() = "http://127.0.0.1"

    override val mcpSsePort: Int
        get() = 3002

    override val mcpRequestTimeoutMillis: Long
        get() = System.getenv("AILEARN_MCP_REQUEST_TIMEOUT_MILLIS")?.toLongOrNull()
            ?: DefaultConfig.DEFAULT_MCP_REQUEST_TIMEOUT_MILLIS
    
    override val ragReranking: Boolean
        get() = System.getenv("AILEARN_RAG_RERANKING")?.toBoolean()
            ?: DefaultConfig.DEFAULT_RAG_RERANKING
    
    override val ragRerankingProvider: String
        get() = System.getenv("AILEARN_RAG_RERANKING_PROVIDER") ?: DefaultConfig.DEFAULT_RAG_RERANKING_PROVIDER
    
    override val ragCandidateCount: Int
        get() = System.getenv("AILEARN_RAG_CANDIDATE_COUNT")?.toIntOrNull()
            ?: DefaultConfig.DEFAULT_RAG_CANDIDATE_COUNT
    
    override val ragRerankModel: String
        get() = System.getenv("AILEARN_RAG_RERANK_MODEL") ?: DefaultConfig.DEFAULT_RAG_RERANK_MODEL
    
    override val ragFilterThreshold: Double
        get() = System.getenv("AILEARN_RAG_FILTER_THRESHOLD")?.toDoubleOrNull()
            ?: DefaultConfig.DEFAULT_RAG_FILTER_THRESHOLD
    
    override val ragHistoryContextSize: Int
        get() = System.getenv("AILEARN_RAG_HISTORY_CONTEXT_SIZE")?.toIntOrNull()
            ?: DefaultConfig.DEFAULT_RAG_HISTORY_CONTEXT_SIZE
    
    override val aiReviewSystemPrompt: String
        get() = System.getenv("AILEARN_AI_REVIEW_SYSTEM_PROMPT")
            ?: DefaultConfig.DEFAULT_AI_REVIEW_SYSTEM_PROMPT
}

/**
 * Configuration loaded from a properties file.
 */
private class FileConfig(private val properties: java.util.Properties) : AppConfig {
    companion object {
        fun tryLoad(fileName: String): FileConfig? {
            val file = File(fileName)
            if (!file.exists()) {
                logger.debug("Config file does not exist: $fileName")
                return null
            }
            
            return try {
                val properties = java.util.Properties()
                file.inputStream().use { properties.load(it) }
                logger.debug("Successfully loaded config file: $fileName")
                FileConfig(properties)
            } catch (e: Exception) {
                logger.warn("Failed to load config file: $fileName", e)
                null
            }
        }
    }
    
    private fun getString(key: String, default: String): String = properties.getProperty(key, default)
    private fun getInt(key: String, default: Int): Int = properties.getProperty(key)?.toIntOrNull() ?: default
    private fun getDouble(key: String, default: Double): Double = properties.getProperty(key)?.toDoubleOrNull() ?: default
    private fun getLong(key: String, default: Long): Long = properties.getProperty(key)?.toLongOrNull() ?: default
    private fun getBoolean(key: String, default: Boolean): Boolean = properties.getProperty(key)?.toBoolean() ?: default
    
    override val apiKey: String get() = getString("api.key", "")
    override val apiUrl: String get() = getString("api.url", DefaultConfig.DEFAULT_API_URL)
    override val model: String get() = getString("model", DefaultConfig.DEFAULT_MODEL)
    override val maxTokens: Int get() = getInt("max.tokens", DefaultConfig.DEFAULT_MAX_TOKENS)
    override val temperature: Double get() = getDouble("temperature", DefaultConfig.DEFAULT_TEMPERATURE)
    override val systemPrompt: String get() = getString("system.prompt", DefaultConfig.DEFAULT_SYSTEM_PROMPT)
    override val dialogEndMarker: String get() = getString("dialog.end.marker", DefaultConfig.DEFAULT_DIALOG_END_MARKER)
    override val pricePerMillionTokens: Double get() = getDouble("price.per.million.tokens", DefaultConfig.DEFAULT_PRICE_PER_MILLION_TOKENS)
    override val requestTimeoutMillis: Long get() = getLong("request.timeout.millis", DefaultConfig.DEFAULT_REQUEST_TIMEOUT_MILLIS)
    override val useMessageHistory: Boolean get() = getBoolean("use.message.history", DefaultConfig.DEFAULT_USE_MESSAGE_HISTORY)
    override val githubToken: String get() = getString("github.token", DefaultConfig.DEFAULT_GITHUB_TOKEN)
    
    override val enableSummarization: Boolean get() = getBoolean("enable.summarization", DefaultConfig.DEFAULT_ENABLE_SUMMARIZATION)
    
    override val summarizationTokenThreshold: Int get() = getInt("summarization.token.threshold", DefaultConfig.DEFAULT_SUMMARIZATION_TOKEN_THRESHOLD)
    override val summarizationModel: String get() = getString("summarization.model", DefaultConfig.DEFAULT_SUMMARIZATION_MODEL)
    override val summarizationMaxTokens: Int get() = getInt("summarization.max.tokens", DefaultConfig.DEFAULT_SUMMARIZATION_MAX_TOKENS)
    override val summarizationTemperature: Double get() = getDouble("summarization.temperature", DefaultConfig.DEFAULT_SUMMARIZATION_TEMPERATURE)
    override val summarizationSystemPrompt: String get() = getString("summarization.system.prompt", DefaultConfig.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT)
    override val summarizationPrompt: String get() = getString("summarization.prompt", DefaultConfig.DEFAULT_SUMMARIZATION_PROMPT)
    
    override val memoryStoreType: String get() = getString("memory.store.type", DefaultConfig.DEFAULT_MEMORY_STORE_TYPE)
    override val memoryStorePath: String? get() = properties.getProperty("memory.store.path")?.takeIf { it.isNotEmpty() }
        ?: if (DefaultConfig.DEFAULT_MEMORY_STORE_PATH.isEmpty()) null else DefaultConfig.DEFAULT_MEMORY_STORE_PATH

    override val mcpSseProtocol: String get() = getString("mcp.sse.protocol", DefaultConfig.DEFAULT_MCP_SSE_PROTOCOL)
    override val mcpSseHost: String get() = getString("mcp.sse.host", DefaultConfig.DEFAULT_MCP_SSE_HOST)
    override val mcpSsePort: Int get() = getInt("mcp.sse.port", DefaultConfig.DEFAULT_MCP_SSE_PORT)
    override val mcpRequestTimeoutMillis: Long get() = getLong("mcp.request.timeout.millis", DefaultConfig.DEFAULT_MCP_REQUEST_TIMEOUT_MILLIS)
    
    override val ragReranking: Boolean get() = getBoolean("rag.reranking", DefaultConfig.DEFAULT_RAG_RERANKING)
    override val ragRerankingProvider: String get() = getString("rag.reranking.provider", DefaultConfig.DEFAULT_RAG_RERANKING_PROVIDER)
    override val ragCandidateCount: Int get() = getInt("rag.candidate.count", DefaultConfig.DEFAULT_RAG_CANDIDATE_COUNT)
    override val ragRerankModel: String get() = getString("rag.rerank.model", DefaultConfig.DEFAULT_RAG_RERANK_MODEL)
    override val ragFilterThreshold: Double get() = getDouble("rag.filter.threshold", DefaultConfig.DEFAULT_RAG_FILTER_THRESHOLD)
    override val ragHistoryContextSize: Int get() = getInt("rag.history.context.size", DefaultConfig.DEFAULT_RAG_HISTORY_CONTEXT_SIZE)
    override val aiReviewSystemPrompt: String get() = getString("ai.review.system.prompt", DefaultConfig.DEFAULT_AI_REVIEW_SYSTEM_PROMPT)
}

/**
 * Configuration wrapper for BuildConfig (build-time injected values).
 * Only provides API key, other values fall back to defaults.
 */
private class BuildConfigWrapper : AppConfig {
    companion object {
        fun tryLoad(): BuildConfigWrapper? {
            return try {
                // Use reflection to access BuildConfig at runtime
                val buildConfigClass = Class.forName("org.pogrom.BuildConfig")
                val apiKeyField = buildConfigClass.getField("API_KEY")
                val apiKey = apiKeyField.get(null) as? String ?: ""
                
                if (apiKey.isNotEmpty()) {
                    logger.debug("BuildConfig API key loaded successfully")
                    BuildConfigWrapper(apiKey)
                } else {
                    logger.debug("BuildConfig API key is empty")
                    null
                }
            } catch (e: ClassNotFoundException) {
                logger.debug("BuildConfig class not found")
                null
            } catch (e: Exception) {
                logger.debug("Failed to load BuildConfig: ${e.message}")
                null
            }
        }
    }
    
    private val apiKeyValue: String
    
    constructor(apiKey: String) {
        this.apiKeyValue = apiKey
    }
    
    override val apiKey: String get() = apiKeyValue
    override val apiUrl: String = DefaultConfig.DEFAULT_API_URL
    override val model: String = DefaultConfig.DEFAULT_MODEL
    override val maxTokens: Int = DefaultConfig.DEFAULT_MAX_TOKENS
    override val temperature: Double = DefaultConfig.DEFAULT_TEMPERATURE
    override val systemPrompt: String = DefaultConfig.DEFAULT_SYSTEM_PROMPT
    override val dialogEndMarker: String = DefaultConfig.DEFAULT_DIALOG_END_MARKER
    override val pricePerMillionTokens: Double = DefaultConfig.DEFAULT_PRICE_PER_MILLION_TOKENS
    override val requestTimeoutMillis: Long = DefaultConfig.DEFAULT_REQUEST_TIMEOUT_MILLIS
    override val useMessageHistory: Boolean = DefaultConfig.DEFAULT_USE_MESSAGE_HISTORY
    override val githubToken: String = DefaultConfig.DEFAULT_GITHUB_TOKEN
    
    override val enableSummarization: Boolean = DefaultConfig.DEFAULT_ENABLE_SUMMARIZATION
    
    override val summarizationTokenThreshold: Int = DefaultConfig.DEFAULT_SUMMARIZATION_TOKEN_THRESHOLD
    override val summarizationModel: String = DefaultConfig.DEFAULT_SUMMARIZATION_MODEL
    override val summarizationMaxTokens: Int = DefaultConfig.DEFAULT_SUMMARIZATION_MAX_TOKENS
    override val summarizationTemperature: Double = DefaultConfig.DEFAULT_SUMMARIZATION_TEMPERATURE
    override val summarizationSystemPrompt: String = DefaultConfig.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT
    override val summarizationPrompt: String = DefaultConfig.DEFAULT_SUMMARIZATION_PROMPT
    
    override val memoryStoreType: String = DefaultConfig.DEFAULT_MEMORY_STORE_TYPE
    override val memoryStorePath: String? = if (DefaultConfig.DEFAULT_MEMORY_STORE_PATH.isEmpty()) null else DefaultConfig.DEFAULT_MEMORY_STORE_PATH

    override val mcpSseProtocol: String = DefaultConfig.DEFAULT_MCP_SSE_PROTOCOL
    override val mcpSseHost: String = DefaultConfig.DEFAULT_MCP_SSE_HOST
    override val mcpSsePort: Int = DefaultConfig.DEFAULT_MCP_SSE_PORT
    override val mcpRequestTimeoutMillis: Long = DefaultConfig.DEFAULT_MCP_REQUEST_TIMEOUT_MILLIS
    
    override val ragReranking: Boolean = DefaultConfig.DEFAULT_RAG_RERANKING
    override val ragRerankingProvider: String = DefaultConfig.DEFAULT_RAG_RERANKING_PROVIDER
    override val ragCandidateCount: Int = DefaultConfig.DEFAULT_RAG_CANDIDATE_COUNT
    override val ragRerankModel: String = DefaultConfig.DEFAULT_RAG_RERANK_MODEL
    override val ragFilterThreshold: Double = DefaultConfig.DEFAULT_RAG_FILTER_THRESHOLD
    override val ragHistoryContextSize: Int = DefaultConfig.DEFAULT_RAG_HISTORY_CONTEXT_SIZE
    override val aiReviewSystemPrompt: String = DefaultConfig.DEFAULT_AI_REVIEW_SYSTEM_PROMPT
}

/**
 * Default configuration with hardcoded values.
 */
private class DefaultAppConfig : AppConfig {
    override val apiKey: String = ""
    override val apiUrl: String = DefaultConfig.DEFAULT_API_URL
    override val model: String = DefaultConfig.DEFAULT_MODEL
    override val maxTokens: Int = DefaultConfig.DEFAULT_MAX_TOKENS
    override val temperature: Double = DefaultConfig.DEFAULT_TEMPERATURE
    override val systemPrompt: String = DefaultConfig.DEFAULT_SYSTEM_PROMPT
    override val dialogEndMarker: String = DefaultConfig.DEFAULT_DIALOG_END_MARKER
    override val pricePerMillionTokens: Double = DefaultConfig.DEFAULT_PRICE_PER_MILLION_TOKENS
    override val requestTimeoutMillis: Long = DefaultConfig.DEFAULT_REQUEST_TIMEOUT_MILLIS
    override val useMessageHistory: Boolean = DefaultConfig.DEFAULT_USE_MESSAGE_HISTORY
    override val githubToken: String = DefaultConfig.DEFAULT_GITHUB_TOKEN
    
    override val enableSummarization: Boolean = DefaultConfig.DEFAULT_ENABLE_SUMMARIZATION
    
    override val summarizationTokenThreshold: Int = DefaultConfig.DEFAULT_SUMMARIZATION_TOKEN_THRESHOLD
    override val summarizationModel: String = DefaultConfig.DEFAULT_SUMMARIZATION_MODEL
    override val summarizationMaxTokens: Int = DefaultConfig.DEFAULT_SUMMARIZATION_MAX_TOKENS
    override val summarizationTemperature: Double = DefaultConfig.DEFAULT_SUMMARIZATION_TEMPERATURE
    override val summarizationSystemPrompt: String = DefaultConfig.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT
    override val summarizationPrompt: String = DefaultConfig.DEFAULT_SUMMARIZATION_PROMPT
    
    override val memoryStoreType: String = DefaultConfig.DEFAULT_MEMORY_STORE_TYPE
    override val memoryStorePath: String? = if (DefaultConfig.DEFAULT_MEMORY_STORE_PATH.isEmpty()) null else DefaultConfig.DEFAULT_MEMORY_STORE_PATH

    override val mcpSseProtocol: String = DefaultConfig.DEFAULT_MCP_SSE_PROTOCOL
    override val mcpSseHost: String = DefaultConfig.DEFAULT_MCP_SSE_HOST
    override val mcpSsePort: Int = DefaultConfig.DEFAULT_MCP_SSE_PORT
    override val mcpRequestTimeoutMillis: Long = DefaultConfig.DEFAULT_MCP_REQUEST_TIMEOUT_MILLIS
    
    override val ragReranking: Boolean = DefaultConfig.DEFAULT_RAG_RERANKING
    override val ragRerankingProvider: String = DefaultConfig.DEFAULT_RAG_RERANKING_PROVIDER
    override val ragCandidateCount: Int = DefaultConfig.DEFAULT_RAG_CANDIDATE_COUNT
    override val ragRerankModel: String = DefaultConfig.DEFAULT_RAG_RERANK_MODEL
    override val ragFilterThreshold: Double = DefaultConfig.DEFAULT_RAG_FILTER_THRESHOLD
    override val ragHistoryContextSize: Int = DefaultConfig.DEFAULT_RAG_HISTORY_CONTEXT_SIZE
    override val aiReviewSystemPrompt: String = DefaultConfig.DEFAULT_AI_REVIEW_SYSTEM_PROMPT
}

/**
 * Composite configuration that falls back to secondary source if primary doesn't have a value.
 */
private class CompositeConfig(
    private val primary: AppConfig,
    private val fallback: AppConfig
) : AppConfig {
    override val apiKey: String get() = primary.apiKey.ifEmpty { fallback.apiKey }
    override val apiUrl: String get() = primary.apiUrl.ifEmpty { fallback.apiUrl }
    override val model: String get() = primary.model.ifEmpty { fallback.model }
    override val maxTokens: Int get() = if (primary.maxTokens > 0) primary.maxTokens else fallback.maxTokens
    override val temperature: Double get() = primary.temperature
    override val systemPrompt: String get() = primary.systemPrompt.ifEmpty { fallback.systemPrompt }
    override val dialogEndMarker: String get() = primary.dialogEndMarker.ifEmpty { fallback.dialogEndMarker }
    override val pricePerMillionTokens: Double get() = primary.pricePerMillionTokens
    override val requestTimeoutMillis: Long get() = primary.requestTimeoutMillis
    override val useMessageHistory: Boolean get() = primary.useMessageHistory
    override val githubToken: String get() = primary.githubToken.ifEmpty { fallback.githubToken }
    
    override val enableSummarization: Boolean get() = primary.enableSummarization
    
    override val summarizationTokenThreshold: Int get() = if (primary.summarizationTokenThreshold > 0) primary.summarizationTokenThreshold else fallback.summarizationTokenThreshold
    override val summarizationModel: String get() = primary.summarizationModel.ifEmpty { fallback.summarizationModel }
    override val summarizationMaxTokens: Int get() = if (primary.summarizationMaxTokens > 0) primary.summarizationMaxTokens else fallback.summarizationMaxTokens
    override val summarizationTemperature: Double get() = primary.summarizationTemperature
    override val summarizationSystemPrompt: String get() = primary.summarizationSystemPrompt.ifEmpty { fallback.summarizationSystemPrompt }
    override val summarizationPrompt: String get() = primary.summarizationPrompt.ifEmpty { fallback.summarizationPrompt }
    
    override val memoryStoreType: String get() = primary.memoryStoreType.ifEmpty { fallback.memoryStoreType }
    override val memoryStorePath: String? get() = primary.memoryStorePath ?: fallback.memoryStorePath

    override val mcpSseProtocol: String get() = primary.mcpSseProtocol.ifEmpty { fallback.mcpSseProtocol }
    override val mcpSseHost: String get() = primary.mcpSseHost.ifEmpty { fallback.mcpSseHost }
    override val mcpSsePort: Int get() = if (primary.mcpSsePort > 0) primary.mcpSsePort else fallback.mcpSsePort
    override val mcpRequestTimeoutMillis: Long get() = if (primary.mcpRequestTimeoutMillis > 0) primary.mcpRequestTimeoutMillis else fallback.mcpRequestTimeoutMillis
    
    override val ragReranking: Boolean get() = primary.ragReranking
    override val ragRerankingProvider: String get() = primary.ragRerankingProvider.ifEmpty { fallback.ragRerankingProvider }
    override val ragCandidateCount: Int get() = if (primary.ragCandidateCount > 0) primary.ragCandidateCount else fallback.ragCandidateCount
    override val ragRerankModel: String get() = primary.ragRerankModel.ifEmpty { fallback.ragRerankModel }
    override val ragFilterThreshold: Double get() = if (primary.ragFilterThreshold > 0) primary.ragFilterThreshold else fallback.ragFilterThreshold
    override val ragHistoryContextSize: Int get() = if (primary.ragHistoryContextSize > 0) primary.ragHistoryContextSize else fallback.ragHistoryContextSize
    override val aiReviewSystemPrompt: String get() = primary.aiReviewSystemPrompt.ifEmpty { fallback.aiReviewSystemPrompt }
}
