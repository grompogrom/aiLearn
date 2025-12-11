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
}

/**
 * Default configuration values.
 */
object DefaultConfig {
    const val DEFAULT_API_URL = "https://api.perplexity.ai/chat/completions"
    const val DEFAULT_MODEL = "sonar"
    const val DEFAULT_MAX_TOKENS = 500
    const val DEFAULT_TEMPERATURE = 0.6
    const val DEFAULT_SYSTEM_PROMPT = "Answers must be short and succinct"
    const val DEFAULT_DIALOG_END_MARKER = "###END###"
    const val DEFAULT_PRICE_PER_MILLION_TOKENS = 1.0
    const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 60_000L
    const val DEFAULT_USE_MESSAGE_HISTORY = true
}
