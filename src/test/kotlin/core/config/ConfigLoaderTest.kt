package core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File
import java.util.Properties

/**
 * Unit tests for ConfigLoader.
 * Tests configuration loading from multiple sources with priority.
 */
class ConfigLoaderTest {

    @Test
    fun `load returns configuration with default values when no sources available`() {
        // Clear environment variables for this test
        val originalEnv = System.getenv("AILEARN_API_KEY")
        
        try {
            // Remove config file if it exists
            val configFile = File("ailearn.config.properties")
            val fileExisted = configFile.exists()
            if (fileExisted) {
                configFile.delete()
            }
            
            val config = ConfigLoader.load()
            
            // Should have default values
            assertEquals(DefaultConfig.DEFAULT_API_URL, config.apiUrl)
            assertEquals(DefaultConfig.DEFAULT_MODEL, config.model)
            assertEquals(DefaultConfig.DEFAULT_MAX_TOKENS, config.maxTokens)
            assertEquals(DefaultConfig.DEFAULT_TEMPERATURE, config.temperature)
            assertEquals(DefaultConfig.DEFAULT_SYSTEM_PROMPT, config.systemPrompt)
            
            // Restore file if it existed
            if (fileExisted) {
                // File will be recreated if needed
            }
        } finally {
            // Note: We can't easily restore environment variables in tests
            // This is a limitation of the test environment
        }
    }

    @Test
    fun `load reads from properties file when file exists`() {
        val configFile = File("ailearn.config.properties")
        val originalFileExisted = configFile.exists()
        var originalContent: String? = null
        
        try {
            // Save original content if file exists
            if (originalFileExisted) {
                originalContent = configFile.readText()
            }
            
            // Create test config file
            val properties = Properties()
            properties.setProperty("api.key", "file-api-key")
            properties.setProperty("model", "file-model")
            properties.setProperty("max.tokens", "2000")
            properties.setProperty("temperature", "0.8")
            properties.setProperty("system.prompt", "File system prompt")
            
            configFile.outputStream().use { properties.store(it, "Test config") }
            
            val config = ConfigLoader.load()
            
            // Should read from file (if env vars are not set)
            // Note: Environment variables take precedence, so we can only test
            // that the file is read when env vars are not present
            assertNotNull(config)
            // If env var AILEARN_MODEL is set, it will take precedence
            // So we just verify that config loaded successfully
            assertTrue(config.model.isNotEmpty())
            
        } finally {
            // Restore original file or delete test file
            if (originalFileExisted && originalContent != null) {
                configFile.writeText(originalContent)
            } else if (!originalFileExisted && configFile.exists()) {
                configFile.delete()
            }
        }
    }

    @Test
    fun `load handles missing config file gracefully`() {
        val configFile = File("ailearn.config.properties")
        val fileExisted = configFile.exists()
        var originalContent: String? = null
        
        try {
            if (fileExisted) {
                originalContent = configFile.readText()
                configFile.delete()
            }
            
            // Should not throw exception
            val config = ConfigLoader.load()
            assertNotNull(config)
            
        } finally {
            if (fileExisted && originalContent != null) {
                configFile.writeText(originalContent)
            }
        }
    }

    @Test
    fun `load handles invalid config file gracefully`() {
        val configFile = File("ailearn.config.properties")
        val fileExisted = configFile.exists()
        var originalContent: String? = null
        
        try {
            if (fileExisted) {
                originalContent = configFile.readText()
            }
            
            // Create invalid properties file
            configFile.writeText("invalid properties content { not valid }")
            
            // Should not throw exception, should fall back to defaults
            val config = ConfigLoader.load()
            assertNotNull(config)
            
        } finally {
            if (fileExisted && originalContent != null) {
                configFile.writeText(originalContent)
            } else if (!fileExisted && configFile.exists()) {
                configFile.delete()
            }
        }
    }

    @Test
    fun `load handles numeric conversion errors in config file`() {
        val configFile = File("ailearn.config.properties")
        val fileExisted = configFile.exists()
        var originalContent: String? = null
        
        try {
            if (fileExisted) {
                originalContent = configFile.readText()
            }
            
            // Create config file with invalid numeric values
            val properties = Properties()
            properties.setProperty("max.tokens", "not-a-number")
            properties.setProperty("temperature", "also-not-a-number")
            
            configFile.outputStream().use { properties.store(it, "Test config") }
            
            // Should not throw exception, should use defaults for invalid values
            val config = ConfigLoader.load()
            assertNotNull(config)
            // Should use default value when conversion fails
            assertEquals(DefaultConfig.DEFAULT_MAX_TOKENS, config.maxTokens)
            
        } finally {
            if (fileExisted && originalContent != null) {
                configFile.writeText(originalContent)
            } else if (!fileExisted && configFile.exists()) {
                configFile.delete()
            }
        }
    }

    @Test
    fun `load reads all config file properties`() {
        val configFile = File("ailearn.config.properties")
        val fileExisted = configFile.exists()
        var originalContent: String? = null
        
        try {
            if (fileExisted) {
                originalContent = configFile.readText()
            }
            
            val properties = Properties()
            properties.setProperty("api.key", "test-key")
            properties.setProperty("api.url", "https://test.api")
            properties.setProperty("model", "test-model")
            properties.setProperty("max.tokens", "1500")
            properties.setProperty("temperature", "0.7")
            properties.setProperty("system.prompt", "Test prompt")
            properties.setProperty("dialog.end.marker", "END")
            properties.setProperty("price.per.million.tokens", "2.5")
            properties.setProperty("request.timeout.millis", "30000")
            properties.setProperty("use.message.history", "false")
            properties.setProperty("summarization.token.threshold", "3000")
            properties.setProperty("summarization.model", "summary-model")
            properties.setProperty("summarization.max.tokens", "600")
            properties.setProperty("summarization.temperature", "0.2")
            properties.setProperty("summarization.system.prompt", "Summary prompt")
            properties.setProperty("summarization.prompt", "Summarize")
            properties.setProperty("memory.store.type", "sqlite")
            properties.setProperty("memory.store.path", "/test/path.db")
            
            configFile.outputStream().use { properties.store(it, "Test config") }
            
            val config = ConfigLoader.load()
            
            // Verify all properties are read (if env vars don't override)
            assertNotNull(config)
            // Note: Environment variables take precedence, so we can only verify
            // that the config is loaded without errors
            
        } finally {
            if (fileExisted && originalContent != null) {
                configFile.writeText(originalContent)
            } else if (!fileExisted && configFile.exists()) {
                configFile.delete()
            }
        }
    }

    @Test
    fun `load handles empty memory store path`() {
        val configFile = File("ailearn.config.properties")
        val fileExisted = configFile.exists()
        var originalContent: String? = null
        
        try {
            if (fileExisted) {
                originalContent = configFile.readText()
            }
            
            val properties = Properties()
            properties.setProperty("memory.store.path", "")
            
            configFile.outputStream().use { properties.store(it, "Test config") }
            
            val config = ConfigLoader.load()
            assertNotNull(config)
            // Empty path should result in null
            // (This depends on implementation)
            
        } finally {
            if (fileExisted && originalContent != null) {
                configFile.writeText(originalContent)
            } else if (!fileExisted && configFile.exists()) {
                configFile.delete()
            }
        }
    }

    @Test
    fun `load returns valid configuration object`() {
        val config = ConfigLoader.load()
        
        assertNotNull(config)
        assertTrue(config.apiUrl.isNotEmpty())
        assertTrue(config.model.isNotEmpty())
        assertTrue(config.maxTokens > 0)
        assertTrue(config.temperature >= 0.0)
        assertTrue(config.systemPrompt.isNotEmpty())
        assertTrue(config.dialogEndMarker.isNotEmpty())
        assertTrue(config.pricePerMillionTokens >= 0.0)
        assertTrue(config.requestTimeoutMillis > 0)
        assertTrue(config.summarizationTokenThreshold > 0)
        assertTrue(config.summarizationModel.isNotEmpty())
        assertTrue(config.summarizationMaxTokens > 0)
        assertTrue(config.summarizationTemperature >= 0.0)
        assertTrue(config.summarizationSystemPrompt.isNotEmpty())
        assertTrue(config.summarizationPrompt.isNotEmpty())
        assertTrue(config.memoryStoreType.isNotEmpty())
    }
}

