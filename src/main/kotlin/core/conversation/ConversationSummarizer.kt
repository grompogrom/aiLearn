package core.conversation

import core.config.AppConfig
import core.domain.ChatRequest
import core.domain.ChatResponse
import core.domain.Message
import core.domain.MessageRole
import core.domain.TokenUsage
import core.provider.LlmProvider

/**
 * Service responsible for conversation summarization.
 * 
 * This component handles:
 * - Deciding whether summarization is needed based on token usage threshold
 * - Building and sending summarization requests with dedicated configuration
 * - Returning the summary text to replace the detailed conversation history
 */
class ConversationSummarizer(
    private val llmProvider: LlmProvider,
    private val config: AppConfig
) {
    /**
     * Determines if summarization should be triggered based on the previous response's token usage.
     * 
     * Summarization is needed when:
     * - The previous response's totalTokens exceeds the configured threshold
     * - Token usage data is available (not null)
     * 
     * @param previousUsage Token usage from the previous AI response
     * @return true if summarization should be triggered, false otherwise
     */
    fun shouldSummarize(previousUsage: TokenUsage?): Boolean {
        val totalTokens = previousUsage?.totalTokens ?: return false
        return totalTokens > config.summarizationTokenThreshold
    }

    /**
     * Summarizes the conversation history by sending a request to the AI model.
     * 
     * This method:
     * - Uses dedicated summarization configuration (model, maxTokens, temperature, systemPrompt)
     * - Sends the full conversation history along with a summarization prompt
     * - Returns the summary text that will replace the detailed history
     * 
     * @param history The current conversation history to summarize
     * @return The summary text from the AI model
     */
    suspend fun summarizeConversation(history: List<Message>): String {
        // Build the summarization request with dedicated configuration
        // The request includes the full history plus the summarization instruction
        val summarizationMessages = buildList {
            // Add the summarization-specific system prompt
            add(Message.create(MessageRole.SYSTEM, config.summarizationSystemPrompt))
            
            // Add all messages from the conversation history (including the original system prompt)
            // The full context helps the model create a better summary
            addAll(history)
            
            // Add the summarization instruction as a user message
            add(Message.create(MessageRole.USER, config.summarizationPrompt))
        }

        val summarizationRequest = ChatRequest(
            model = config.summarizationModel,
            messages = summarizationMessages,
            maxTokens = config.summarizationMaxTokens,
            temperature = config.summarizationTemperature
        )

        val response: ChatResponse = llmProvider.sendRequest(summarizationRequest)
        return response.content.trim()
    }
}
