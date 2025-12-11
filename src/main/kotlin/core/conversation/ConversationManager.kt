package core.conversation

import core.config.AppConfig
import core.domain.ChatRequest
import core.domain.ChatResponse
import core.domain.Message
import core.domain.MessageRole
import core.domain.TokenUsage
import core.provider.LlmProvider

/**
 * Callback type for summarization events.
 * Called when summarization starts and completes.
 */
typealias SummarizationCallback = (isStarting: Boolean) -> Unit

/**
 * Manages conversation state and handles chat requests.
 * This is the core business logic that is independent of any specific LLM provider or frontend.
 */
class ConversationManager(
    private val llmProvider: LlmProvider,
    private val config: AppConfig,
    private val summarizationCallback: SummarizationCallback? = null
) {
    private val messageHistory = mutableListOf<Message>().apply {
        if (config.useMessageHistory) {
            add(Message.create(MessageRole.SYSTEM, config.systemPrompt))
        }
    }
    
    // Track the last response's token usage to determine if summarization is needed
    private var lastResponseUsage: TokenUsage? = null
    
    // Summarizer instance for handling conversation summarization
    private val summarizer = ConversationSummarizer(llmProvider, config)

    /**
     * Sends a request with conversation history (if enabled).
     * 
     * Before processing the new request, this method checks if summarization is needed
     * based on the previous response's token usage. If the threshold is exceeded:
     * 1. Notifies the callback that summarization is starting
     * 2. Sends a summarization request to the AI
     * 3. Replaces the conversation history with [system prompt + summary]
     * 4. Then proceeds with the normal request flow
     */
    suspend fun sendRequest(userContent: String, temperature: Double? = null): ChatResponse {
        // Check if summarization is needed before processing the new request
        // This check uses the previous response's token usage to decide
        if (config.useMessageHistory && summarizer.shouldSummarize(lastResponseUsage)) {
            // Notify that summarization is starting
            summarizationCallback?.invoke(true)
            
            // Get the current history (including system prompt) for summarization
            val currentHistory = messageHistory.toList()
            
            // Request summary from the AI using dedicated summarization configuration
            val summary = summarizer.summarizeConversation(currentHistory)
            
            // Replace the conversation history with system prompt only
            // The summary will be prepended to the new user message to maintain proper message alternation
            // (SYSTEM -> USER -> ASSISTANT pattern required by the API)
            messageHistory.clear()
            messageHistory.add(Message.create(MessageRole.SYSTEM, config.systemPrompt))
            
            // Notify that summarization is complete
            summarizationCallback?.invoke(false)
            
            // Prepend summary to the new user message to provide context
            // This ensures proper message alternation (no consecutive USER messages)
            val userContentWithSummary = "Previous conversation summary: $summary\n\n$userContent"
            
            // Proceed with the normal request flow, using the combined message
            val request = if (config.useMessageHistory) {
                messageHistory.add(Message.create(MessageRole.USER, userContentWithSummary))
                createChatRequest(temperature ?: config.temperature)
            } else {
                val messages = listOf(
                    Message.create(MessageRole.SYSTEM, config.systemPrompt),
                    Message.create(MessageRole.USER, userContentWithSummary)
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
                // Store the response usage for the next summarization check
                lastResponseUsage = response.usage
            }
            
            return response
        }
        
        // Proceed with the normal request flow (when summarization is not needed)
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
            // Store the response usage for the next summarization check
            lastResponseUsage = response.usage
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
        lastResponseUsage = null
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
