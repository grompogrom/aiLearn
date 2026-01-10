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
 * Unit tests for SqliteMemoryStore.
 */
class SqliteMemoryStoreTest {
    
    private val testDbFile = File("test_history.db")
    
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
        override val memoryStoreType: String = "sqlite"
        override val memoryStorePath: String? = testDbFile.absolutePath
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
        if (testDbFile.exists()) {
            testDbFile.delete()
        }
        // Also clean up any journal files
        File("test_history.db-journal").takeIf { it.exists() }?.delete()
        File("test_history.db-wal").takeIf { it.exists() }?.delete()
        File("test_history.db-shm").takeIf { it.exists() }?.delete()
    }
    
    @Test
    fun `saveHistory creates database and stores messages`() = runBlocking {
        val store = SqliteMemoryStore(testConfig)
        val messages = listOf(
            Message(MessageRole.SYSTEM, "System message"),
            Message(MessageRole.USER, "User message"),
            Message(MessageRole.ASSISTANT, "Assistant message")
        )
        
        store.saveHistory(messages)
        
        assertTrue(testDbFile.exists(), "Database file should be created")
        val loaded = store.loadHistory()
        assertEquals(3, loaded.size)
        assertEquals(messages, loaded)
        store.close()
    }
    
    @Test
    fun `loadHistory returns empty list when database is empty`() = runBlocking {
        val store = SqliteMemoryStore(testConfig)
        val loaded = store.loadHistory()
        assertTrue(loaded.isEmpty(), "Should return empty list when database is empty")
        store.close()
    }
    
    @Test
    fun `loadHistory returns saved messages in correct order`() = runBlocking {
        val store = SqliteMemoryStore(testConfig)
        val messages = listOf(
            Message(MessageRole.SYSTEM, "First"),
            Message(MessageRole.USER, "Second"),
            Message(MessageRole.ASSISTANT, "Third")
        )
        
        store.saveHistory(messages)
        val loaded = store.loadHistory()
        
        assertEquals(3, loaded.size)
        assertEquals("First", loaded[0].content)
        assertEquals("Second", loaded[1].content)
        assertEquals("Third", loaded[2].content)
        store.close()
    }
    
    @Test
    fun `loadHistory preserves disableSearch flag`() = runBlocking {
        val store = SqliteMemoryStore(testConfig)
        val messages = listOf(
            Message(MessageRole.USER, "Test message", disableSearch = false)
        )
        
        store.saveHistory(messages)
        val loaded = store.loadHistory()
        
        assertEquals(1, loaded.size)
        assertEquals(false, loaded[0].disableSearch)
        store.close()
    }
    
    @Test
    fun `clearHistory removes all messages`() = runBlocking {
        val store = SqliteMemoryStore(testConfig)
        val messages = listOf(
            Message(MessageRole.USER, "Test")
        )
        
        store.saveHistory(messages)
        store.clearHistory()
        
        val loaded = store.loadHistory()
        assertTrue(loaded.isEmpty(), "History should be empty after clear")
        store.close()
    }
    
    @Test
    fun `saveHistory overwrites existing messages`() = runBlocking {
        val store = SqliteMemoryStore(testConfig)
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

