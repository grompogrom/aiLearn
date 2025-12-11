package core.domain

/**
 * Represents a request to an LLM provider.
 */
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val maxTokens: Int = 1000,
    val temperature: Double = 0.3
)
