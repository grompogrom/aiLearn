package core.conversation

import core.config.AppConfig
import core.domain.ChatRequest
import core.domain.ChatResponse
import core.domain.Message
import core.domain.MessageRole
import core.domain.TokenUsage
import core.mcp.McpService
import core.memory.MemoryStore
import core.provider.LlmProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    private val summarizationCallback: SummarizationCallback? = null,
    private val memoryStore: MemoryStore? = null,
    private val mcpService: McpService? = null
) {
    private val messageHistory = mutableListOf<Message>()
    private val saveScope = CoroutineScope(Dispatchers.IO)
    
    // Tool calling handler - created if MCP service is available
    private val toolCallingHandler = mcpService?.let {
        ToolCallingHandler(llmProvider, config, it)
    }
    
    /**
     * Initializes the conversation manager by loading history from memory store if available.
     * Should be called after construction if memory persistence is enabled.
     */
    suspend fun initialize() {
        if (config.useMessageHistory && memoryStore != null) {
            val loadedHistory = memoryStore.loadHistory()
            if (loadedHistory.isNotEmpty()) {
                messageHistory.clear()
                messageHistory.addAll(loadedHistory)
                return
            }
        }
        
        // If no history loaded, initialize with system prompt
        if (config.useMessageHistory) {
            messageHistory.add(Message.create(MessageRole.SYSTEM, config.systemPrompt))
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
     * based on the previous response's token usage. If the threshold is exceeded and
     * summarization is enabled:
     * 1. Notifies the callback that summarization is starting
     * 2. Sends a summarization request to the AI
     * 3. Replaces the conversation history with [system prompt + summary]
     * 4. Then proceeds with the normal request flow
     * 
     * If MCP service is available and message history is enabled, tool calling is used.
     */
    suspend fun sendRequest(userContent: String, temperature: Double? = null): ChatResponse {
        // Check if summarization is needed before processing the new request
        // This check uses the previous response's token usage to decide
        if (shouldTriggerSummarization()) {
            return handleSummarizationAndContinue(userContent, temperature)
        }
        
        // Proceed with the normal request flow (when summarization is not needed)
        return processRequest(userContent, temperature)
    }
    
    /**
     * Determines if summarization should be triggered.
     * Summarization is triggered when:
     * - Message history is enabled
     * - Summarization is enabled in config
     * - Previous response's token usage exceeds threshold
     */
    private fun shouldTriggerSummarization(): Boolean {
        return config.useMessageHistory 
            && config.enableSummarization 
            && summarizer.shouldSummarize(lastResponseUsage)
    }
    
    /**
     * Handles the summarization process and continues with the request.
     */
    private suspend fun handleSummarizationAndContinue(
        userContent: String,
        temperature: Double?
    ): ChatResponse {
        // Notify that summarization is starting
        summarizationCallback?.invoke(true)
        
        // Get the current history (including system prompt) for summarization
        val currentHistory = messageHistory.toList()
        
        // Request summary from the AI using dedicated summarization configuration
        val summary = summarizer.summarizeConversation(currentHistory)
        
        // Replace the conversation history with system prompt enhanced with summary context
        // This maintains proper message alternation while providing context
        messageHistory.clear()
        val enhancedSystemPrompt = buildString {
            append(config.systemPrompt)
            if (summary.isNotBlank()) {
                append("\n\nPrevious conversation summary: ")
                append(summary)
            }
        }
        messageHistory.add(Message.create(MessageRole.SYSTEM, enhancedSystemPrompt))
        
        // Save the new history state
        saveHistoryAsync()
        
        // Notify that summarization is complete
        summarizationCallback?.invoke(false)
        
        // Now process the actual user request with the summary context in the system prompt
        return processRequest(userContent, temperature)
    }
    
    /**
     * Processes a request, using tool calling if available, otherwise normal flow.
     */
    private suspend fun processRequest(userContent: String, temperature: Double?): ChatResponse {
        // Use tool calling handler if available and message history is enabled
        if (toolCallingHandler != null && config.useMessageHistory) {
            val response = toolCallingHandler.processWithTools(
                userContent,
                messageHistory,
                temperature
            )
            // Track usage
            lastResponseUsage = response.usage
            saveHistoryAsync()
            return response
        } else {
            // Normal flow without tools
            return sendRequestInternal(userContent, temperature)
        }
    }

    /**
     * Clears the conversation history and resets token usage tracking.
     * If message history is enabled, reinitializes with system prompt.
     */
    suspend fun clearHistory() {
        messageHistory.clear()
        lastResponseUsage = null
        
        if (config.useMessageHistory) {
            messageHistory.add(Message.create(MessageRole.SYSTEM, config.systemPrompt))
        }
        
        clearMemoryStore()
    }
    
    /**
     * Clears the memory store, handling errors gracefully.
     */
    private suspend fun clearMemoryStore() {
        if (memoryStore == null) return
        
        try {
            memoryStore.clearHistory()
            if (config.useMessageHistory) {
                // Save the new state with just system prompt
                memoryStore.saveHistory(messageHistory.toList())
            }
        } catch (e: Exception) {
            // Log error but don't crash - persistence is best effort
            System.err.println("Warning: Failed to clear history: ${e.message}")
        }
    }

    /**
     * Internal method to send a request with the given user content.
     * Handles request creation, sending, and response tracking.
     */
    private suspend fun sendRequestInternal(userContent: String, temperature: Double?): ChatResponse {
        val request = if (config.useMessageHistory) {
            messageHistory.add(Message.create(MessageRole.USER, userContent))
            // Save history after adding user message
            saveHistoryAsync()
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
            // Save history after adding assistant response
            saveHistoryAsync()
        }

        return response
    }
    
    /**
     * Saves history asynchronously without blocking the main flow.
     */
    private fun saveHistoryAsync() {
        if (config.useMessageHistory && memoryStore != null) {
            saveScope.launch {
                try {
                    memoryStore.saveHistory(messageHistory.toList())
                } catch (e: Exception) {
                    // Log error but don't crash - persistence is best effort
                    System.err.println("Warning: Failed to save conversation history: ${e.message}")
                }
            }
        }
    }

    private fun createChatRequest(temperature: Double): ChatRequest {
        return ChatRequest(
            model = config.model,
            messages = messageHistory.toList(),
            maxTokens = config.maxTokens,
            temperature = temperature
        )
    }
    
    /**
     * Sends a request without saving to conversation history.
     * This is useful for background tasks like reminder checks that should not
     * pollute the conversation history.
     * 
     * @param userContent The user message content
     * @param temperature Optional temperature override (defaults to config temperature)
     * @return ChatResponse with content and token usage
     */
    suspend fun sendRequestWithoutHistory(userContent: String, temperature: Double? = null): ChatResponse {
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
}
