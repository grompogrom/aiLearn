package core.conversation

import core.config.AppConfig
import core.domain.TokenUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for TokenCostCalculator.
 * Tests token cost calculation and formatting.
 */
class TokenCostCalculatorTest {

    @Test
    fun `calculatePrice returns zero for zero tokens`() {
        val config = createTestConfig(pricePerMillion = 1.0)
        val calculator = TokenCostCalculator(config)
        
        assertEquals(0.0, calculator.calculatePrice(0))
    }

    @Test
    fun `calculatePrice calculates correctly for one million tokens`() {
        val config = createTestConfig(pricePerMillion = 1.0)
        val calculator = TokenCostCalculator(config)
        
        assertEquals(1.0, calculator.calculatePrice(1_000_000))
    }

    @Test
    fun `calculatePrice calculates correctly for half million tokens`() {
        val config = createTestConfig(pricePerMillion = 1.0)
        val calculator = TokenCostCalculator(config)
        
        assertEquals(0.5, calculator.calculatePrice(500_000))
    }

    @Test
    fun `calculatePrice calculates correctly for custom price per million`() {
        val config = createTestConfig(pricePerMillion = 2.5)
        val calculator = TokenCostCalculator(config)
        
        assertEquals(2.5, calculator.calculatePrice(1_000_000))
        assertEquals(1.25, calculator.calculatePrice(500_000))
    }

    @Test
    fun `calculatePrice calculates correctly for small token counts`() {
        val config = createTestConfig(pricePerMillion = 1.0)
        val calculator = TokenCostCalculator(config)
        
        // 1000 tokens = 0.001 million
        assertEquals(0.001, calculator.calculatePrice(1_000))
        
        // 100 tokens = 0.0001 million
        assertEquals(0.0001, calculator.calculatePrice(100))
    }

    @Test
    fun `formatTokenUsage returns empty string for null usage`() {
        val config = createTestConfig()
        val calculator = TokenCostCalculator(config)
        
        assertEquals("", calculator.formatTokenUsage(null))
    }

    @Test
    fun `formatTokenUsage formats complete usage correctly`() {
        val config = createTestConfig(pricePerMillion = 1.0)
        val calculator = TokenCostCalculator(config)
        
        val usage = TokenUsage(
            promptTokens = 100,
            completionTokens = 200,
            totalTokens = 300
        )
        
        val formatted = calculator.formatTokenUsage(usage)
        
        assertTrue(formatted.contains("Prompt tokens: 100"))
        assertTrue(formatted.contains("Completion tokens: 200"))
        assertTrue(formatted.contains("Total tokens: 300"))
        assertTrue(formatted.contains("Price: $"))
        assertTrue(formatted.contains("0.000300"))
    }

    @Test
    fun `formatTokenUsage formats usage with only totalTokens`() {
        val config = createTestConfig(pricePerMillion = 1.0)
        val calculator = TokenCostCalculator(config)
        
        val usage = TokenUsage(totalTokens = 5000)
        
        val formatted = calculator.formatTokenUsage(usage)
        
        assertTrue(formatted.contains("Total tokens: 5000"))
        assertTrue(formatted.contains("Price: $"))
        assertTrue(!formatted.contains("Prompt tokens:"))
        assertTrue(!formatted.contains("Completion tokens:"))
    }

    @Test
    fun `formatTokenUsage formats usage with only promptTokens`() {
        val config = createTestConfig(pricePerMillion = 1.0)
        val calculator = TokenCostCalculator(config)
        
        val usage = TokenUsage(promptTokens = 1000)
        
        val formatted = calculator.formatTokenUsage(usage)
        
        assertTrue(formatted.contains("Prompt tokens: 1000"))
        assertTrue(!formatted.contains("Total tokens:"))
        assertTrue(!formatted.contains("Price:"))
    }

    @Test
    fun `formatTokenUsage includes proper formatting with separators`() {
        val config = createTestConfig(pricePerMillion = 1.0)
        val calculator = TokenCostCalculator(config)
        
        val usage = TokenUsage(
            promptTokens = 100,
            completionTokens = 200,
            totalTokens = 300
        )
        
        val formatted = calculator.formatTokenUsage(usage)
        
        assertTrue(formatted.contains("--- Token Usage ---"))
        assertTrue(formatted.contains("------------------"))
    }

    @Test
    fun `formatTokenUsage calculates price correctly in formatted output`() {
        val config = createTestConfig(pricePerMillion = 2.5)
        val calculator = TokenCostCalculator(config)
        
        val usage = TokenUsage(totalTokens = 1_000_000)
        
        val formatted = calculator.formatTokenUsage(usage)
        
        assertTrue(formatted.contains("Price: $2.500000"))
    }

    @Test
    fun `formatTokenUsage handles large token counts`() {
        val config = createTestConfig(pricePerMillion = 1.0)
        val calculator = TokenCostCalculator(config)
        
        val usage = TokenUsage(
            promptTokens = 1_000_000,
            completionTokens = 2_000_000,
            totalTokens = 3_000_000
        )
        
        val formatted = calculator.formatTokenUsage(usage)
        
        assertTrue(formatted.contains("Prompt tokens: 1000000"))
        assertTrue(formatted.contains("Completion tokens: 2000000"))
        assertTrue(formatted.contains("Total tokens: 3000000"))
        assertTrue(formatted.contains("Price: $3.000000"))
    }

    @Test
    fun `formatTokenUsage handles optional fields correctly`() {
        val config = createTestConfig(pricePerMillion = 1.0)
        val calculator = TokenCostCalculator(config)
        
        val usage = TokenUsage(
            promptTokens = 100,
            completionTokens = null,
            totalTokens = 100,
            searchContextSize = "large",
            citationTokens = 50,
            numSearchQueries = 3,
            reasoningTokens = 25
        )
        
        val formatted = calculator.formatTokenUsage(usage)
        
        // Should only show non-null fields
        assertTrue(formatted.contains("Prompt tokens: 100"))
        assertTrue(formatted.contains("Total tokens: 100"))
        assertTrue(!formatted.contains("Completion tokens:"))
    }

    private fun createTestConfig(pricePerMillion: Double = 1.0): AppConfig {
        return object : AppConfig {
            override val apiKey: String = "test-key"
            override val apiUrl: String = "https://test.api"
            override val model: String = "test-model"
            override val maxTokens: Int = 1000
            override val temperature: Double = 0.6
            override val systemPrompt: String = "Test prompt"
            override val dialogEndMarker: String = "###END###"
            override val pricePerMillionTokens: Double = pricePerMillion
            override val requestTimeoutMillis: Long = 60000
            override val useMessageHistory: Boolean = true
            override val enableSummarization: Boolean = true
            override val summarizationTokenThreshold: Int = 2000
            override val summarizationModel: String = "test-model"
            override val summarizationMaxTokens: Int = 500
            override val summarizationTemperature: Double = 0.3
            override val summarizationSystemPrompt: String = "Summarize"
            override val summarizationPrompt: String = "Summarize this"
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
        }
    }
}

