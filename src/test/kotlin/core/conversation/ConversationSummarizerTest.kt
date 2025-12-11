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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for ConversationSummarizer.
 * Tests the logic for deciding when to summarize and the summarization request flow.
 */
class ConversationSummarizerTest {

    @Test
    fun `shouldSummarize returns false when usage is null`() {
        val config = createTestConfig(threshold = 2000)
        val provider = createMockProvider()
        val summarizer = ConversationSummarizer(provider, config)
        
        assertFalse(summarizer.shouldSummarize(null))
    }

    @Test
    fun `shouldSummarize returns false when totalTokens is below threshold`() {
        val config = createTestConfig(threshold = 2000)
        val provider = createMockProvider()
        val summarizer = ConversationSummarizer(provider, config)
        
        val usage = TokenUsage(totalTokens = 1500)
        assertFalse(summarizer.shouldSummarize(usage))
    }

    @Test
    fun `shouldSummarize returns false when totalTokens equals threshold`() {
        val config = createTestConfig(threshold = 2000)
        val provider = createMockProvider()
        val summarizer = ConversationSummarizer(provider, config)
        
        val usage = TokenUsage(totalTokens = 2000)
        assertFalse(summarizer.shouldSummarize(usage))
    }

    @Test
    fun `shouldSummarize returns true when totalTokens exceeds threshold`() {
        val config = createTestConfig(threshold = 2000)
        val provider = createMockProvider()
        val summarizer = ConversationSummarizer(provider, config)
        
        val usage = TokenUsage(totalTokens = 2001)
        assertTrue(summarizer.shouldSummarize(usage))
    }

    @Test
    fun `shouldSummarize uses configured threshold`() {
        val config = createTestConfig(threshold = 5000)
        val provider = createMockProvider()
        val summarizer = ConversationSummarizer(provider, config)
        
        val usage = TokenUsage(totalTokens = 2000)
        assertFalse(summarizer.shouldSummarize(usage))
        
        val usage2 = TokenUsage(totalTokens = 5001)
        assertTrue(summarizer.shouldSummarize(usage2))
    }

    @Test
    fun `summarizeConversation sends request with correct configuration`() = runBlocking {
        var capturedRequest: ChatRequest? = null
        val summaryText = "Summary: User asked about Kotlin and coroutines. Assistant explained both topics."
        
        val config = createTestConfig(
            summarizationModel = "sonar-summary",
            summarizationMaxTokens = 300,
            summarizationTemperature = 0.2,
            summarizationSystemPrompt = "You are a summarizer",
            summarizationPrompt = "Summarize this conversation"
        )
        
        val provider = object : LlmProvider {
            override suspend fun sendRequest(request: ChatRequest): ChatResponse {
                capturedRequest = request
                return ChatResponse(content = summaryText, usage = null)
            }
            
            override fun close() {
                // No-op for mock
            }
        }
        
        val summarizer = ConversationSummarizer(provider, config)
        val history = listOf(
            Message.create(MessageRole.SYSTEM, "Original system prompt"),
            Message.create(MessageRole.USER, "What is Kotlin?"),
            Message.create(MessageRole.ASSISTANT, "Kotlin is a programming language..."),
            Message.create(MessageRole.USER, "What are coroutines?"),
            Message.create(MessageRole.ASSISTANT, "Coroutines are lightweight threads...")
        )
        
        val result = summarizer.summarizeConversation(history)
        
        // Verify the summary is returned
        assertTrue(result == summaryText)
        
        // Verify the request uses summarization configuration
        assertTrue(capturedRequest != null)
        assertTrue(capturedRequest!!.model == config.summarizationModel)
        assertTrue(capturedRequest!!.maxTokens == config.summarizationMaxTokens)
        assertTrue(capturedRequest!!.temperature == config.summarizationTemperature)
        
        // Verify the request includes summarization system prompt
        val systemMessages = capturedRequest!!.messages.filter { it.role == MessageRole.SYSTEM }
        assertTrue(systemMessages.isNotEmpty())
        assertTrue(systemMessages.first().content == config.summarizationSystemPrompt)
        
        // Verify the request includes the summarization prompt as a user message
        val userMessages = capturedRequest!!.messages.filter { it.role == MessageRole.USER }
        assertTrue(userMessages.isNotEmpty())
        assertTrue(userMessages.last().content == config.summarizationPrompt)
    }

    @Test
    fun `summarizeConversation includes full history including original system prompt`() = runBlocking {
        var capturedRequest: ChatRequest? = null
        
        val config = createTestConfig()
        val provider = object : LlmProvider {
            override suspend fun sendRequest(request: ChatRequest): ChatResponse {
                capturedRequest = request
                return ChatResponse(content = "Summary", usage = null)
            }
            
            override fun close() {
                // No-op for mock
            }
        }
        
        val summarizer = ConversationSummarizer(provider, config)
        val history = listOf(
            Message.create(MessageRole.SYSTEM, "Original system prompt"),
            Message.create(MessageRole.USER, "Question 1"),
            Message.create(MessageRole.ASSISTANT, "Answer 1"),
            Message.create(MessageRole.USER, "Question 2"),
            Message.create(MessageRole.ASSISTANT, "Answer 2")
        )
        
        summarizer.summarizeConversation(history)
        
        // Verify that the summarization system prompt is first
        assertTrue(capturedRequest!!.messages.first().role == MessageRole.SYSTEM)
        assertTrue(capturedRequest!!.messages.first().content == config.summarizationSystemPrompt)
        
        // Verify that all original history messages are included
        val originalSystemPrompt = capturedRequest!!.messages.find { 
            it.role == MessageRole.SYSTEM && it.content == "Original system prompt" 
        }
        assertNotNull(originalSystemPrompt, "Original system prompt should be included in summarization request")
        
        // Verify conversation messages are included
        val userMessages = capturedRequest!!.messages.filter { 
            it.role == MessageRole.USER && it.content != config.summarizationPrompt 
        }
        assertTrue(userMessages.size >= 2, "Original user messages should be included")
        
        // Verify summarization prompt is last
        assertTrue(capturedRequest!!.messages.last().role == MessageRole.USER)
        assertTrue(capturedRequest!!.messages.last().content == config.summarizationPrompt)
    }

    // Helper functions to create test objects
    private fun createTestConfig(
        threshold: Int = 2000,
        summarizationModel: String = "sonar",
        summarizationMaxTokens: Int = 500,
        summarizationTemperature: Double = 0.3,
        summarizationSystemPrompt: String = "Summarize conversations",
        summarizationPrompt: String = "Provide a summary"
    ): AppConfig {
        return object : AppConfig {
            override val apiKey: String = "test-key"
            override val apiUrl: String = "https://test.api"
            override val model: String = "test-model"
            override val maxTokens: Int = 1000
            override val temperature: Double = 0.6
            override val systemPrompt: String = "Test prompt"
            override val dialogEndMarker: String = "###END###"
            override val pricePerMillionTokens: Double = 1.0
            override val requestTimeoutMillis: Long = 60000
            override val useMessageHistory: Boolean = true
            override val summarizationTokenThreshold: Int = threshold
            override val summarizationModel: String = summarizationModel
            override val summarizationMaxTokens: Int = summarizationMaxTokens
            override val summarizationTemperature: Double = summarizationTemperature
            override val summarizationSystemPrompt: String = summarizationSystemPrompt
            override val summarizationPrompt: String = summarizationPrompt
        }
    }

    private fun createMockProvider(): LlmProvider {
        return object : LlmProvider {
            override suspend fun sendRequest(request: ChatRequest): ChatResponse {
                return ChatResponse(content = "Mock response", usage = null)
            }
            
            override fun close() {
                // No-op for mock
            }
        }
    }
}
