package api.ollama

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class OllamaClientTest {

    private fun createMockClient(responseJson: String, statusCode: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            engine {
                addHandler { request ->
                    respond(
                        content = responseJson,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
    }

    @Test
    fun `test embedText with single input`() = runBlocking {
        val responseJson = """
            {
                "model": "mxbai-embed-large",
                "embeddings": [[0.1, 0.2, 0.3, 0.4]]
            }
        """.trimIndent()
        
        val mockClient = createMockClient(responseJson)
        val ollamaClient = OllamaClient("http://localhost:11434", mockClient)
        
        val embeddings = ollamaClient.embedText("mxbai-embed-large", listOf("test text"))
        
        assertEquals(1, embeddings.size)
        assertEquals(4, embeddings[0].size)
        assertEquals(0.1f, embeddings[0][0], 0.001f)
        assertEquals(0.4f, embeddings[0][3], 0.001f)
    }

    @Test
    fun `test embedText with multiple inputs`() = runBlocking {
        val responseJson = """
            {
                "model": "mxbai-embed-large",
                "embeddings": [
                    [0.1, 0.2, 0.3],
                    [0.4, 0.5, 0.6],
                    [0.7, 0.8, 0.9]
                ]
            }
        """.trimIndent()
        
        val mockClient = createMockClient(responseJson)
        val ollamaClient = OllamaClient("http://localhost:11434", mockClient)
        
        val embeddings = ollamaClient.embedText("mxbai-embed-large", listOf("text1", "text2", "text3"))
        
        assertEquals(3, embeddings.size)
        assertEquals(3, embeddings[0].size)
        assertEquals(0.1f, embeddings[0][0], 0.001f)
        assertEquals(0.9f, embeddings[2][2], 0.001f)
    }

    @Test
    fun `test embedText with empty input list`() = runBlocking {
        val responseJson = """
            {
                "model": "mxbai-embed-large",
                "embeddings": []
            }
        """.trimIndent()
        
        val mockClient = createMockClient(responseJson)
        val ollamaClient = OllamaClient("http://localhost:11434", mockClient)
        
        val embeddings = ollamaClient.embedText("mxbai-embed-large", emptyList())
        
        assertTrue(embeddings.isEmpty())
    }

    @Test
    fun `test embedText handles server error gracefully`() = runBlocking {
        val responseJson = """{"error": "Model not found"}"""
        
        val mockClient = createMockClient(responseJson, HttpStatusCode.NotFound)
        val ollamaClient = OllamaClient("http://localhost:11434", mockClient)
        
        assertThrows<Exception> {
            ollamaClient.embedText("invalid-model", listOf("test"))
        }
    }

    @Test
    fun `test embedText with realistic embedding dimensions`() = runBlocking {
        // mxbai-embed-large produces 1024-dimensional embeddings
        val embedding = List(1024) { it * 0.001f }
        val responseJson = """
            {
                "model": "mxbai-embed-large",
                "embeddings": [${embedding.joinToString(",")}]
            }
        """.trimIndent()
        
        val mockClient = createMockClient(responseJson)
        val ollamaClient = OllamaClient("http://localhost:11434", mockClient)
        
        val embeddings = ollamaClient.embedText("mxbai-embed-large", listOf("test"))
        
        assertEquals(1, embeddings.size)
        assertEquals(1024, embeddings[0].size)
    }

    @Test
    fun `test embedText handles special characters in input`() = runBlocking {
        val responseJson = """
            {
                "model": "mxbai-embed-large",
                "embeddings": [[0.1, 0.2]]
            }
        """.trimIndent()
        
        val mockClient = createMockClient(responseJson)
        val ollamaClient = OllamaClient("http://localhost:11434", mockClient)
        
        val embeddings = ollamaClient.embedText(
            "mxbai-embed-large",
            listOf("Text with special chars: !@#$%^&*()_+{}[]|\\:\";<>?,./")
        )
        
        assertEquals(1, embeddings.size)
    }

    @Test
    fun `test embedText handles unicode characters`() = runBlocking {
        val responseJson = """
            {
                "model": "mxbai-embed-large",
                "embeddings": [[0.1, 0.2]]
            }
        """.trimIndent()
        
        val mockClient = createMockClient(responseJson)
        val ollamaClient = OllamaClient("http://localhost:11434", mockClient)
        
        val embeddings = ollamaClient.embedText(
            "mxbai-embed-large",
            listOf("Unicode: 你好 мир 🌍")
        )
        
        assertEquals(1, embeddings.size)
    }

    @Test
    fun `test embedText with long text`() = runBlocking {
        val responseJson = """
            {
                "model": "mxbai-embed-large",
                "embeddings": [[0.1, 0.2, 0.3]]
            }
        """.trimIndent()
        
        val mockClient = createMockClient(responseJson)
        val ollamaClient = OllamaClient("http://localhost:11434", mockClient)
        
        val longText = "a".repeat(10000)
        val embeddings = ollamaClient.embedText("mxbai-embed-large", listOf(longText))
        
        assertEquals(1, embeddings.size)
    }

    @Test
    fun `test embedText with batch processing`() = runBlocking {
        val batchSize = 10
        val embeddings = List(batchSize) { List(1024) { 0.1f } }
        val embeddingsJson = embeddings.joinToString(",", "[", "]") { embedding ->
            embedding.joinToString(",", "[", "]")
        }
        
        val responseJson = """
            {
                "model": "mxbai-embed-large",
                "embeddings": $embeddingsJson
            }
        """.trimIndent()
        
        val mockClient = createMockClient(responseJson)
        val ollamaClient = OllamaClient("http://localhost:11434", mockClient)
        
        val inputs = List(batchSize) { "chunk $it" }
        val result = ollamaClient.embedText("mxbai-embed-large", inputs)
        
        assertEquals(batchSize, result.size)
        result.forEach { embedding ->
            assertEquals(1024, embedding.size)
        }
    }

    @Test
    fun `test default model parameter`() = runBlocking {
        val responseJson = """
            {
                "model": "mxbai-embed-large",
                "embeddings": [[0.1]]
            }
        """.trimIndent()
        
        val mockClient = createMockClient(responseJson)
        val ollamaClient = OllamaClient("http://localhost:11434", mockClient)
        
        // Should work with default model
        val embeddings = ollamaClient.embedText(input = listOf("test"))
        
        assertEquals(1, embeddings.size)
    }
}

