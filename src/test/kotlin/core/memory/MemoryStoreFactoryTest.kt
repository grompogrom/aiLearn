package core.memory

import core.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

/**
 * Unit tests for MemoryStoreFactory.
 */
class MemoryStoreFactoryTest {
    
    private fun createTestConfig(memoryStoreType: String): AppConfig {
        return object : AppConfig {
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
            override val memoryStoreType: String = memoryStoreType
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
        }
    }
    
    @Test
    fun `create returns JsonMemoryStore for json type`() {
        val config = createTestConfig("json")
        val store = MemoryStoreFactory.create(config)
        assertIs<JsonMemoryStore>(store)
        store.close()
    }
    
    @Test
    fun `create returns JsonMemoryStore for JSON type (case insensitive)`() {
        val config = createTestConfig("JSON")
        val store = MemoryStoreFactory.create(config)
        assertIs<JsonMemoryStore>(store)
        store.close()
    }
    
    @Test
    fun `create returns SqliteMemoryStore for sqlite type`() {
        val config = createTestConfig("sqlite")
        val store = MemoryStoreFactory.create(config)
        assertIs<SqliteMemoryStore>(store)
        store.close()
    }
    
    @Test
    fun `create returns SqliteMemoryStore for SQLITE type (case insensitive)`() {
        val config = createTestConfig("SQLITE")
        val store = MemoryStoreFactory.create(config)
        assertIs<SqliteMemoryStore>(store)
        store.close()
    }
    
    @Test
    fun `create throws IllegalArgumentException for unsupported type`() {
        val config = createTestConfig("invalid")
        assertFailsWith<IllegalArgumentException> {
            MemoryStoreFactory.create(config)
        }
    }
}

