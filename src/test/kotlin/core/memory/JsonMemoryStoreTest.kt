package core.memory

import core.config.AppConfig
import core.domain.Message
import core.domain.MessageRole
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

/**
 * Unit tests for JsonMemoryStore.
 */
class JsonMemoryStoreTest {
    
    private val testFile = File("test_history.json")
    
    
    private val testConfig = object : AppConfig {
        override val apiKey: String = "test-key"
        override val apiUrl: String = "https://test.api"
        override val model: String = "test-model"
        override val maxTokens: Int = 100
        override val temperature: Double = 0.7
        override val systemPrompt: String = "Test prompt"
        override val dialogEndMarker: String = "END"
        override val pricePerMillionTokens: Double = 1.0
        override val requestTimeoutMillis: Long = 5000
        override val useMessageHistory: Boolean = true
        override val enableSummarization: Boolean = true
        override val summarizationTokenThreshold: Int = 1000
        override val summarizationModel: String = "test-model"
        override val summarizationMaxTokens: Int = 500
        override val summarizationTemperature: Double = 0.3
        override val summarizationSystemPrompt: String = "Summarize"
        override val summarizationPrompt: String = "Summarize this"
        override val memoryStoreType: String = "json"
        override val memoryStorePath: String? = testFile.absolutePath
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
    
    @AfterTest
    fun cleanup() {
        if (testFile.exists()) {
            testFile.delete()
        }
    }
    
    @Test
    fun `saveHistory creates file and stores messages`() = runBlocking {
        val store = JsonMemoryStore(testConfig)
        val messages = listOf(
            Message(MessageRole.SYSTEM, "System message"),
            Message(MessageRole.USER, "User message"),
            Message(MessageRole.ASSISTANT, "Assistant message")
        )
        
        store.saveHistory(messages)
        
        assertTrue(testFile.exists(), "History file should be created")
        val loaded = store.loadHistory()
        assertEquals(3, loaded.size)
        assertEquals(messages, loaded)
        store.close()
    }
    
    @Test
    fun `loadHistory returns empty list when file does not exist`() = runBlocking {
        val store = JsonMemoryStore(testConfig)
        val loaded = store.loadHistory()
        assertTrue(loaded.isEmpty(), "Should return empty list when file doesn't exist")
        store.close()
    }
    
    @Test
    fun `loadHistory returns saved messages`() = runBlocking {
        val store = JsonMemoryStore(testConfig)
        val messages = listOf(
            Message(MessageRole.USER, "Test message", disableSearch = false)
        )
        
        store.saveHistory(messages)
        val loaded = store.loadHistory()
        
        assertEquals(1, loaded.size)
        assertEquals("Test message", loaded[0].content)
        assertEquals(MessageRole.USER, loaded[0].role)
        assertEquals(false, loaded[0].disableSearch)
        store.close()
    }
    
    @Test
    fun `clearHistory deletes the file`() = runBlocking {
        val store = JsonMemoryStore(testConfig)
        val messages = listOf(Message(MessageRole.USER, "Test"))
        
        store.saveHistory(messages)
        assertTrue(testFile.exists())
        
        store.clearHistory()
        
        val loaded = store.loadHistory()
        assertTrue(loaded.isEmpty(), "History should be empty after clear")
        store.close()
    }
    
    @Test
    fun `saveHistory overwrites existing file`() = runBlocking {
        val store = JsonMemoryStore(testConfig)
        val messages1 = listOf(Message(MessageRole.USER, "First"))
        val messages2 = listOf(Message(MessageRole.USER, "Second"))
        
        store.saveHistory(messages1)
        store.saveHistory(messages2)
        
        val loaded = store.loadHistory()
        assertEquals(1, loaded.size)
        assertEquals("Second", loaded[0].content)
        store.close()
    }
}

