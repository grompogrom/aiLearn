package core.conversation

import core.config.AppConfig
import core.domain.ChatRequest
import core.domain.ChatResponse
import core.domain.Message
import core.domain.MessageRole
import core.provider.LlmProvider

/**
 * Manages conversation state and handles chat requests.
 * This is the core business logic that is independent of any specific LLM provider or frontend.
 */
class ConversationManager(
    private val llmProvider: LlmProvider,
    private val config: AppConfig
) {
    private val messageHistory = mutableListOf<Message>().apply {
        if (config.useMessageHistory) {
            add(Message.create(MessageRole.SYSTEM, config.systemPrompt))
        }
    }

    /**
     * Sends a request with conversation history (if enabled).
     */
    suspend fun sendRequest(userContent: String, temperature: Double? = null): ChatResponse {
        val request = if (config.useMessageHistory) {
            messageHistory.add(Message.create(MessageRole.USER, userContent))
            createChatRequest(temperature ?: config.temperature)
        } else {
            val messages = listOf(
                Message.create(MessageRole.SYSTEM, config.systemPrompt),
                Message.create(MessageRole.USER, userContent)
            )
            ChatRequest(
                model = config.model,
                messages = messages,
                maxTokens = config.maxTokens,
                temperature = temperature ?: config.temperature
            )
        }

        val response = llmProvider.sendRequest(request)

        if (config.useMessageHistory) {
            messageHistory.add(Message.create(MessageRole.ASSISTANT, response.content))
        }

        return response
    }

    /**
     * Sends an independent request without using shared message history.
     * Useful for isolated queries that should not affect conversation context.
     */
    suspend fun sendIndependentRequest(userContent: String, temperature: Double? = null): ChatResponse {
        val messages = listOf(
            Message.create(MessageRole.SYSTEM, config.systemPrompt),
            Message.create(MessageRole.USER, userContent)
        )

        val request = ChatRequest(
            model = config.model,
            messages = messages,
            maxTokens = config.maxTokens,
            temperature = temperature ?: config.temperature
        )

        return llmProvider.sendRequest(request)
    }

    /**
     * Clears the conversation history.
     */
    fun clearHistory() {
        messageHistory.clear()
        if (config.useMessageHistory) {
            messageHistory.add(Message.create(MessageRole.SYSTEM, config.systemPrompt))
        }
    }

    /**
     * Gets the current conversation history.
     */
    fun getHistory(): List<Message> = messageHistory.toList()

    private fun createChatRequest(temperature: Double): ChatRequest {
        return ChatRequest(
            model = config.model,
            messages = messageHistory.toList(),
            maxTokens = config.maxTokens,
            temperature = temperature
        )
    }
}
