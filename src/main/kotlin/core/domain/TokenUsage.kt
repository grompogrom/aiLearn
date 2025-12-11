package core.domain

/**
 * Represents token usage statistics from an LLM API response.
 */
data class TokenUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val searchContextSize: String? = null,
    val citationTokens: Int? = null,
    val numSearchQueries: Int? = null,
    val reasoningTokens: Int? = null
)
