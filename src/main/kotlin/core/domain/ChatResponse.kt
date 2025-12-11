package core.domain

/**
 * Represents a response from the LLM with content and token usage.
 */
data class ChatResponse(
    val content: String,
    val usage: TokenUsage?
)
