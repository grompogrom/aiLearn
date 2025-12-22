package api.ollama

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class OllamaModelsTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `test EmbedRequest serialization with single input`() {
        val request = EmbedRequest(
            model = "mxbai-embed-large",
            input = listOf("test text")
        )
        
        val serialized = json.encodeToString(request)
        
        assertTrue(serialized.contains("\"model\""))
        assertTrue(serialized.contains("mxbai-embed-large"))
        assertTrue(serialized.contains("\"input\""))
        assertTrue(serialized.contains("test text"))
    }

    @Test
    fun `test EmbedRequest serialization with multiple inputs`() {
        val request = EmbedRequest(
            model = "mxbai-embed-large",
            input = listOf("text1", "text2", "text3")
        )
        
        val serialized = json.encodeToString(request)
        
        assertTrue(serialized.contains("text1"))
        assertTrue(serialized.contains("text2"))
        assertTrue(serialized.contains("text3"))
    }

    @Test
    fun `test EmbedRequest with default model`() {
        val request = EmbedRequest(
            input = listOf("test")
        )
        
        assertEquals("mxbai-embed-large", request.model)
    }

    @Test
    fun `test EmbedRequest with empty input list`() {
        val request = EmbedRequest(
            model = "test-model",
            input = emptyList()
        )
        
        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<EmbedRequest>(serialized)
        
        assertEquals(0, deserialized.input.size)
    }

    @Test
    fun `test EmbedRequest with special characters`() {
        val specialText = "Special: !@#$%^&*()_+{}[]|\\:\";<>?,./"
        val request = EmbedRequest(
            model = "test-model",
            input = listOf(specialText)
        )
        
        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<EmbedRequest>(serialized)
        
        assertEquals(specialText, deserialized.input[0])
    }

    @Test
    fun `test EmbedRequest with unicode characters`() {
        val unicodeText = "Unicode: 你好 мир 🌍"
        val request = EmbedRequest(
            model = "test-model",
            input = listOf(unicodeText)
        )
        
        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<EmbedRequest>(serialized)
        
        assertEquals(unicodeText, deserialized.input[0])
    }

    @Test
    fun `test EmbedRequest with long text`() {
        val longText = "a".repeat(10000)
        val request = EmbedRequest(
            model = "test-model",
            input = listOf(longText)
        )
        
        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<EmbedRequest>(serialized)
        
        assertEquals(10000, deserialized.input[0].length)
    }

    @Test
    fun `test EmbedRequest deserialization from JSON`() {
        val jsonString = """
            {
                "model": "custom-model",
                "input": ["chunk1", "chunk2"]
            }
        """.trimIndent()
        
        val request = json.decodeFromString<EmbedRequest>(jsonString)
        
        assertEquals("custom-model", request.model)
        assertEquals(2, request.input.size)
        assertEquals("chunk1", request.input[0])
        assertEquals("chunk2", request.input[1])
    }

    @Test
    fun `test EmbedResponse deserialization with single embedding`() {
        val jsonString = """
            {
                "model": "mxbai-embed-large",
                "embeddings": [[0.1, 0.2, 0.3]]
            }
        """.trimIndent()
        
        val response = json.decodeFromString<EmbedResponse>(jsonString)
        
        assertEquals("mxbai-embed-large", response.model)
        assertEquals(1, response.embeddings.size)
        assertEquals(3, response.embeddings[0].size)
        assertEquals(0.1f, response.embeddings[0][0])
        assertEquals(0.3f, response.embeddings[0][2])
    }

    @Test
    fun `test EmbedResponse deserialization with multiple embeddings`() {
        val jsonString = """
            {
                "model": "mxbai-embed-large",
                "embeddings": [
                    [0.1, 0.2],
                    [0.3, 0.4],
                    [0.5, 0.6]
                ]
            }
        """.trimIndent()
        
        val response = json.decodeFromString<EmbedResponse>(jsonString)
        
        assertEquals(3, response.embeddings.size)
        assertEquals(0.4f, response.embeddings[1][1])
        assertEquals(0.6f, response.embeddings[2][1])
    }

    @Test
    fun `test EmbedResponse with realistic embedding dimensions`() {
        val embedding = List(1024) { it * 0.001f }
        val embeddingJson = embedding.joinToString(",", "[", "]")
        val jsonString = """
            {
                "model": "mxbai-embed-large",
                "embeddings": [$embeddingJson]
            }
        """.trimIndent()
        
        val response = json.decodeFromString<EmbedResponse>(jsonString)
        
        assertEquals(1, response.embeddings.size)
        assertEquals(1024, response.embeddings[0].size)
        assertEquals(0.0f, response.embeddings[0][0], 0.001f)
        assertEquals(1.023f, response.embeddings[0][1023], 0.001f)
    }

    @Test
    fun `test EmbedResponse with negative values`() {
        val jsonString = """
            {
                "model": "test-model",
                "embeddings": [[-0.5, -0.3, 0.2, -0.1]]
            }
        """.trimIndent()
        
        val response = json.decodeFromString<EmbedResponse>(jsonString)
        
        assertEquals(-0.5f, response.embeddings[0][0])
        assertEquals(-0.3f, response.embeddings[0][1])
        assertEquals(0.2f, response.embeddings[0][2])
        assertEquals(-0.1f, response.embeddings[0][3])
    }

    @Test
    fun `test EmbedResponse with zero embeddings`() {
        val jsonString = """
            {
                "model": "test-model",
                "embeddings": []
            }
        """.trimIndent()
        
        val response = json.decodeFromString<EmbedResponse>(jsonString)
        
        assertEquals(0, response.embeddings.size)
    }

    @Test
    fun `test EmbedResponse serialization`() {
        val response = EmbedResponse(
            model = "test-model",
            embeddings = listOf(
                listOf(0.1f, 0.2f),
                listOf(0.3f, 0.4f)
            )
        )
        
        val serialized = json.encodeToString(response)
        
        assertTrue(serialized.contains("\"model\""))
        assertTrue(serialized.contains("test-model"))
        assertTrue(serialized.contains("\"embeddings\""))
        assertTrue(serialized.contains("0.1"))
        assertTrue(serialized.contains("0.4"))
    }

    @Test
    fun `test EmbedResponse round-trip serialization`() {
        val original = EmbedResponse(
            model = "mxbai-embed-large",
            embeddings = listOf(
                listOf(0.1f, 0.2f, 0.3f),
                listOf(0.4f, 0.5f, 0.6f)
            )
        )
        
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<EmbedResponse>(serialized)
        
        assertEquals(original.model, deserialized.model)
        assertEquals(original.embeddings.size, deserialized.embeddings.size)
        assertEquals(original.embeddings[0].size, deserialized.embeddings[0].size)
        assertEquals(original.embeddings[0][0], deserialized.embeddings[0][0])
        assertEquals(original.embeddings[1][2], deserialized.embeddings[1][2])
    }

    @Test
    fun `test EmbedRequest round-trip serialization`() {
        val original = EmbedRequest(
            model = "custom-model",
            input = listOf("text1", "text2", "text3")
        )
        
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<EmbedRequest>(serialized)
        
        assertEquals(original.model, deserialized.model)
        assertEquals(original.input.size, deserialized.input.size)
        assertEquals(original.input, deserialized.input)
    }

    @Test
    fun `test EmbedResponse with very large embeddings`() {
        val largeEmbedding = List(10000) { it * 0.0001f }
        val response = EmbedResponse(
            model = "test-model",
            embeddings = listOf(largeEmbedding)
        )
        
        val serialized = json.encodeToString(response)
        val deserialized = json.decodeFromString<EmbedResponse>(serialized)
        
        assertEquals(10000, deserialized.embeddings[0].size)
        assertEquals(largeEmbedding[0], deserialized.embeddings[0][0])
        assertEquals(largeEmbedding[9999], deserialized.embeddings[0][9999])
    }

    @Test
    fun `test EmbedRequest with batch of inputs`() {
        val batchInputs = List(100) { "chunk $it" }
        val request = EmbedRequest(
            model = "mxbai-embed-large",
            input = batchInputs
        )
        
        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<EmbedRequest>(serialized)
        
        assertEquals(100, deserialized.input.size)
        assertEquals("chunk 0", deserialized.input[0])
        assertEquals("chunk 99", deserialized.input[99])
    }

    @Test
    fun `test EmbedResponse with batch of embeddings`() {
        val batchEmbeddings = List(100) { List(1024) { idx -> idx * 0.001f } }
        val response = EmbedResponse(
            model = "mxbai-embed-large",
            embeddings = batchEmbeddings
        )
        
        val serialized = json.encodeToString(response)
        val deserialized = json.decodeFromString<EmbedResponse>(serialized)
        
        assertEquals(100, deserialized.embeddings.size)
        deserialized.embeddings.forEach { embedding ->
            assertEquals(1024, embedding.size)
        }
    }

    @Test
    fun `test EmbedRequest with multiline text`() {
        val multilineText = """
            Line 1
            Line 2
            Line 3
        """.trimIndent()
        
        val request = EmbedRequest(
            model = "test-model",
            input = listOf(multilineText)
        )
        
        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<EmbedRequest>(serialized)
        
        assertEquals(multilineText, deserialized.input[0])
    }

    @Test
    fun `test EmbedResponse with extremely small values`() {
        val jsonString = """
            {
                "model": "test-model",
                "embeddings": [[0.00001, -0.00001, 0.000001]]
            }
        """.trimIndent()
        
        val response = json.decodeFromString<EmbedResponse>(jsonString)
        
        assertEquals(0.00001f, response.embeddings[0][0], 0.000001f)
        assertEquals(-0.00001f, response.embeddings[0][1], 0.000001f)
        assertEquals(0.000001f, response.embeddings[0][2], 0.0000001f)
    }

    @Test
    fun `test EmbedResponse with mixed precision values`() {
        val jsonString = """
            {
                "model": "test-model",
                "embeddings": [[0.1, 0.123456, -0.987654321, 1.0]]
            }
        """.trimIndent()
        
        val response = json.decodeFromString<EmbedResponse>(jsonString)
        
        assertEquals(0.1f, response.embeddings[0][0], 0.0001f)
        assertEquals(0.123456f, response.embeddings[0][1], 0.000001f)
        assertEquals(-0.987654321f, response.embeddings[0][2], 0.000001f)
        assertEquals(1.0f, response.embeddings[0][3], 0.0001f)
    }

    @Test
    fun `test EmbedRequest ignores unknown JSON fields`() {
        val jsonString = """
            {
                "model": "test-model",
                "input": ["test"],
                "unknown_field": "should be ignored",
                "another_field": 123
            }
        """.trimIndent()
        
        // Should not throw exception
        val request = json.decodeFromString<EmbedRequest>(jsonString)
        
        assertEquals("test-model", request.model)
        assertEquals(1, request.input.size)
    }

    @Test
    fun `test EmbedResponse ignores unknown JSON fields`() {
        val jsonString = """
            {
                "model": "test-model",
                "embeddings": [[0.1]],
                "extra_field": "ignored",
                "metadata": {"key": "value"}
            }
        """.trimIndent()
        
        // Should not throw exception
        val response = json.decodeFromString<EmbedResponse>(jsonString)
        
        assertEquals("test-model", response.model)
        assertEquals(1, response.embeddings.size)
    }

    @Test
    fun `test EmbedResponse with zero-length embedding vectors`() {
        val jsonString = """
            {
                "model": "test-model",
                "embeddings": [[]]
            }
        """.trimIndent()
        
        val response = json.decodeFromString<EmbedResponse>(jsonString)
        
        assertEquals(1, response.embeddings.size)
        assertEquals(0, response.embeddings[0].size)
    }

    @Test
    fun `test EmbedRequest with empty strings`() {
        val request = EmbedRequest(
            model = "test-model",
            input = listOf("", "non-empty", "")
        )
        
        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<EmbedRequest>(serialized)
        
        assertEquals(3, deserialized.input.size)
        assertEquals("", deserialized.input[0])
        assertEquals("non-empty", deserialized.input[1])
        assertEquals("", deserialized.input[2])
    }
}

