package core.config

/**
 * Application configuration interface.
 * Implementations can read from files, environment variables, or other sources.
 */
interface AppConfig {
    val apiKey: String
    val apiUrl: String
    val model: String
    val maxTokens: Int
    val temperature: Double
    val systemPrompt: String
    val dialogEndMarker: String
    val pricePerMillionTokens: Double
    val requestTimeoutMillis: Long
    val useMessageHistory: Boolean
    
    // Summarization configuration
    val enableSummarization: Boolean
    val summarizationTokenThreshold: Int
    val summarizationModel: String
    val summarizationMaxTokens: Int
    val summarizationTemperature: Double
    val summarizationSystemPrompt: String
    val summarizationPrompt: String
    
    /**
     * Type of memory store to use for persisting conversation history.
     * Values: "json" or "sqlite"
     * Default: "json"
     */
    val memoryStoreType: String
    
    /**
     * Optional path for memory store file/database.
     * If not specified, uses default location in current working directory.
     */
    val memoryStorePath: String?

    // MCP (Model Context Protocol) SSE configuration
    val mcpSseProtocol: String
    val mcpSseHost: String
    val mcpSsePort: Int
    val mcpRequestTimeoutMillis: Long
    
    // RAG re-ranking configuration
    val ragReranking: Boolean
    val ragRerankingProvider: String
    val ragCandidateCount: Int
    val ragRerankModel: String
}

/**
 * Default configuration values.
 */
object DefaultConfig {
    const val DEFAULT_API_URL = "https://api.perplexity.ai/chat/completions"
    const val DEFAULT_MODEL = "sonar"
    const val DEFAULT_MAX_TOKENS = 5000
    const val DEFAULT_TEMPERATURE = 0.3
    const val DEFAULT_SYSTEM_PROMPT = "Answers must be short and succinct"
    const val DEFAULT_DIALOG_END_MARKER = "###END###"
    const val DEFAULT_PRICE_PER_MILLION_TOKENS = 1.0
    const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 60_000L
    const val DEFAULT_USE_MESSAGE_HISTORY = true
    
    // Summarization defaults
    const val DEFAULT_ENABLE_SUMMARIZATION = true
    const val DEFAULT_SUMMARIZATION_TOKEN_THRESHOLD = 4000
    const val DEFAULT_SUMMARIZATION_MODEL = "sonar"
    const val DEFAULT_SUMMARIZATION_MAX_TOKENS = 500
    const val DEFAULT_SUMMARIZATION_TEMPERATURE = 0.3
    const val DEFAULT_SUMMARIZATION_SYSTEM_PROMPT = "You are a helpful assistant that summarizes conversations concisely."
    const val DEFAULT_SUMMARIZATION_PROMPT = "Please provide a brief summary of the conversation so far, capturing the key topics, questions, and answers discussed. Keep it concise and focused on the main points."
    
    // Memory store defaults
    const val DEFAULT_MEMORY_STORE_TYPE = "json"
    const val DEFAULT_MEMORY_STORE_PATH = ""  // Empty string means use default location

    // MCP defaults
    const val DEFAULT_MCP_SSE_PROTOCOL = "http"
    const val DEFAULT_MCP_SSE_HOST = ""
    const val DEFAULT_MCP_SSE_PORT = 3002
    const val DEFAULT_MCP_SSE_PATH = "/sse"
    const val DEFAULT_MCP_REQUEST_TIMEOUT_MILLIS = 15_000L
    
    // RAG re-ranking defaults
    const val DEFAULT_RAG_RERANKING = false
    const val DEFAULT_RAG_RERANKING_PROVIDER = "ollama"
    const val DEFAULT_RAG_CANDIDATE_COUNT = 15
    const val DEFAULT_RAG_RERANK_MODEL = "qwen2.5"  // Can also use "qwen2.5:latest"
}
