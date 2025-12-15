package core.config

import java.io.File

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
            val envConfig = EnvironmentConfig()
            val fileConfig = FileConfig.tryLoad(CONFIG_FILE_NAME)
            val buildConfig = BuildConfigWrapper.tryLoad()
            
            // Chain: env -> file -> buildConfig -> defaults
            val fileOrBuild = fileConfig ?: buildConfig
            val finalFallback = fileOrBuild ?: DefaultAppConfig()
            
            return CompositeConfig(
                primary = envConfig,
                fallback = finalFallback
            )
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
        get() = 3001

    override val mcpRequestTimeoutMillis: Long
        get() = System.getenv("AILEARN_MCP_REQUEST_TIMEOUT_MILLIS")?.toLongOrNull()
            ?: DefaultConfig.DEFAULT_MCP_REQUEST_TIMEOUT_MILLIS
}

/**
 * Configuration loaded from a properties file.
 */
private class FileConfig(private val properties: java.util.Properties) : AppConfig {
    companion object {
        fun tryLoad(fileName: String): FileConfig? {
            val file = File(fileName)
            if (!file.exists()) return null
            
            return try {
                val properties = java.util.Properties()
                file.inputStream().use { properties.load(it) }
                FileConfig(properties)
            } catch (e: Exception) {
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
                    BuildConfigWrapper(apiKey)
                } else {
                    null
                }
            } catch (e: Exception) {
                // BuildConfig not available or API_KEY not set
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
}
