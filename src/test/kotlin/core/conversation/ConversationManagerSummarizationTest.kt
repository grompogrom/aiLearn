package core.conversation

import core.config.AppConfig
import core.domain.ChatRequest
import core.domain.ChatResponse
import core.domain.Message
import core.domain.MessageRole
import core.domain.TokenUsage
import core.provider.LlmProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for ConversationManager's summarization functionality.
 * Tests the complete flow: threshold check, summarization trigger, history replacement.
 */
class ConversationManagerSummarizationTest {

    @Test
    fun `sendRequest does not trigger summarization when usage is below threshold`() = runBlocking {
        var summarizationCalled = false
        var callbackInvoked = false
        
        val config = createTestConfig(threshold = 2000)
        val provider = createMockProvider { request: ChatRequest ->
            ChatResponse(
                content = "Response",
                usage = TokenUsage(totalTokens = 1500) // Below threshold
            )
        }
        
        val callback: SummarizationCallback = { isStarting ->
            callbackInvoked = true
            summarizationCalled = isStarting
        }
        
        val manager = ConversationManager(provider, config, callback)
        
        // First request - should not trigger summarization
        val response1 = manager.sendRequest("First question")
        
        assertTrue(response1.content == "Response")
        assertFalse(callbackInvoked, "Callback should not be invoked when below threshold")
        
        // Second request - still should not trigger (previous was 1500, below 2000)
        val response2 = manager.sendRequest("Second question")
        
        assertTrue(response2.content == "Response")
        assertFalse(callbackInvoked, "Callback should not be invoked when below threshold")
    }

    @Test
    fun `sendRequest triggers summarization when usage exceeds threshold`() = runBlocking {
        var summarizationRequestCount = 0
        var callbackInvocationCount = 0
        var callbackStates = mutableListOf<Boolean>()
        
        val config = createTestConfig(threshold = 2000)
        val provider = createMockProvider { request ->
            // Detect summarization request by checking if it uses summarization model
            if (request.model == config.summarizationModel) {
                summarizationRequestCount++
                ChatResponse(
                    content = "Conversation summary: User asked questions, assistant provided answers.",
                    usage = null
                )
            } else {
                // Regular request
                ChatResponse(
                    content = "Regular response",
                    usage = TokenUsage(totalTokens = 2500) // Exceeds threshold
                )
            }
        }
        
        val callback: SummarizationCallback = { isStarting ->
            callbackInvocationCount++
            callbackStates.add(isStarting)
        }
        
        val manager = ConversationManager(provider, config, callback)
        
        // First request - exceeds threshold, but no previous usage to check
        val response1 = manager.sendRequest("First question")
        assertTrue(response1.content == "Regular response")
        assertEquals(0, summarizationRequestCount, "Summarization should not be triggered on first request")
        assertEquals(0, callbackInvocationCount, "Callback should not be invoked on first request")
        
        // Second request - should trigger summarization because previous response exceeded threshold
        val response2 = manager.sendRequest("Second question")
        
        assertEquals(1, summarizationRequestCount, "Summarization request should be made")
        assertEquals(2, callbackInvocationCount, "Callback should be invoked twice (start and end)")
        assertTrue(callbackStates[0], "First callback should indicate summarization starting")
        assertFalse(callbackStates[1], "Second callback should indicate summarization ending")
        assertTrue(response2.content == "Regular response")
    }

    @Test
    fun `sendRequest replaces history with system prompt and summary after summarization`() = runBlocking {
        var capturedSummarizationRequest: ChatRequest? = null
        var capturedRegularRequest: ChatRequest? = null
        
        val config = createTestConfig(threshold = 2000)
        val provider = createMockProvider { request ->
            if (request.model == config.summarizationModel) {
                capturedSummarizationRequest = request
                ChatResponse(
                    content = "Summary: Previous conversation about Kotlin and coroutines.",
                    usage = null
                )
            } else {
                capturedRegularRequest = request
                ChatResponse(
                    content = "Regular response",
                    usage = TokenUsage(totalTokens = 2500)
                )
            }
        }
        
        val manager = ConversationManager(provider, config, null)
        
        // First request
        manager.sendRequest("What is Kotlin?")
        
        // Second request - should trigger summarization
        manager.sendRequest("What are coroutines?")
        
        // Verify that the regular request after summarization includes:
        // 1. System prompt with summary context
        // 2. User message with the actual request
        assertNotNull(capturedRegularRequest)
        val messages = capturedRegularRequest!!.messages
        
        // Should have: system prompt (with summary), user message
        assertTrue(messages.size == 2, "History should contain system prompt with summary and user message")
        assertTrue(messages[0].role == MessageRole.SYSTEM)
        // System prompt should contain both the original prompt and the summary
        val systemPrompt = messages[0].content
        assertTrue(systemPrompt.contains(config.systemPrompt), "System prompt should contain original prompt")
        assertTrue(systemPrompt.contains("Previous conversation summary:"), "System prompt should contain summary label")
        assertTrue(systemPrompt.contains("Summary: Previous conversation about Kotlin and coroutines."), "System prompt should contain summary content")
        
        // The user message should contain only the actual request
        val userMessage = messages[1]
        assertTrue(userMessage.role == MessageRole.USER)
        assertTrue(userMessage.content == "What are coroutines?", "User message should contain only the actual request")
    }

    @Test
    fun `sendRequest does not trigger summarization when useMessageHistory is false`() = runBlocking {
        var summarizationRequestCount = 0
        
        val config = createTestConfig(threshold = 2000, useMessageHistory = false)
        val provider = createMockProvider { request ->
            if (request.model == config.summarizationModel) {
                summarizationRequestCount++
            }
            ChatResponse(
                content = "Response",
                usage = TokenUsage(totalTokens = 2500)
            )
        }
        
        val manager = ConversationManager(provider, config, null)
        
        manager.sendRequest("Question 1")
        manager.sendRequest("Question 2")
        
        assertEquals(0, summarizationRequestCount, "Summarization should not be triggered when history is disabled")
    }

    @Test
    fun `sendRequest does not trigger summarization when enableSummarization is false`() = runBlocking {
        var summarizationRequestCount = 0
        var callbackInvoked = false
        
        val config = createTestConfig(threshold = 2000, enableSummarization = false)
        val provider = createMockProvider { request ->
            if (request.model == config.summarizationModel) {
                summarizationRequestCount++
            }
            ChatResponse(
                content = "Response",
                usage = TokenUsage(totalTokens = 2500) // Exceeds threshold
            )
        }
        
        val callback: SummarizationCallback = { isStarting ->
            callbackInvoked = true
        }
        
        val manager = ConversationManager(provider, config, callback)
        
        // First request
        manager.sendRequest("Question 1")
        
        // Second request - should NOT trigger summarization even though threshold is exceeded
        manager.sendRequest("Question 2")
        
        assertEquals(0, summarizationRequestCount, "Summarization should not be triggered when disabled")
        assertFalse(callbackInvoked, "Callback should not be invoked when summarization is disabled")
    }

    @Test
    fun `clearHistory resets lastResponseUsage`() = runBlocking {
        var requestCount = 0
        
        val config = createTestConfig(threshold = 2000)
        val provider = createMockProvider { request ->
            requestCount++
            if (request.model == config.summarizationModel) {
                ChatResponse(content = "Summary", usage = null)
            } else {
                ChatResponse(
                    content = "Response",
                    usage = TokenUsage(totalTokens = 2500)
                )
            }
        }
        
        val manager = ConversationManager(provider, config, null)
        
        // First request
        manager.sendRequest("Question 1")
        assertEquals(1, requestCount)
        
        // Second request - should trigger summarization
        manager.sendRequest("Question 2")
        assertEquals(3, requestCount) // 1 regular + 1 summarization + 1 regular
        
        // Clear history
        manager.clearHistory()
        
        // Next request - should NOT trigger summarization because lastResponseUsage was reset
        manager.sendRequest("Question 3")
        assertEquals(4, requestCount) // Only 1 more regular request, no summarization
    }

    // Helper functions
    private fun createTestConfig(
        threshold: Int = 2000,
        useMessageHistory: Boolean = true,
        enableSummarization: Boolean = true
    ): AppConfig {
        return object : AppConfig {
            override val apiKey: String = "test-key"
            override val apiUrl: String = "https://test.api"
            override val model: String = "test-model"
            override val maxTokens: Int = 1000
            override val temperature: Double = 0.6
            override val systemPrompt: String = "Test system prompt"
            override val dialogEndMarker: String = "###END###"
            override val pricePerMillionTokens: Double = 1.0
            override val requestTimeoutMillis: Long = 60000
            override val useMessageHistory: Boolean = useMessageHistory
            override val enableSummarization: Boolean = enableSummarization
            override val summarizationTokenThreshold: Int = threshold
            override val summarizationModel: String = "summarization-model"
            override val summarizationMaxTokens: Int = 500
            override val summarizationTemperature: Double = 0.3
            override val summarizationSystemPrompt: String = "You are a summarizer"
            override val summarizationPrompt: String = "Summarize this conversation"
            override val memoryStoreType: String = "json"
            override val memoryStorePath: String? = null
            override val mcpSseProtocol: String = "http"
            override val mcpSseHost: String = ""
            override val mcpSsePort: Int = 3002
            override val mcpRequestTimeoutMillis: Long = 15000
            override val ragReranking: Boolean = false
            override val ragRerankingProvider: String = "ollama"
            override val ragCandidateCount: Int = 15
            override val ragRerankModel: String = "qwen2.5"
            override val ragFilterThreshold: Double = 0.7
            override val ragHistoryContextSize: Int = 5
        }
    }

    private fun createMockProvider(
        responseProvider: (ChatRequest) -> ChatResponse
    ): LlmProvider {
        return object : LlmProvider {
            override suspend fun sendRequest(request: ChatRequest): ChatResponse {
                return responseProvider(request)
            }
            
            override fun close() {
                // No-op for mock
            }
        }
    }
}
