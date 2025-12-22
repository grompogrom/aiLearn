package api.ollama

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(OllamaClient::class.java)

/**
 * Client for interacting with Ollama's embedding API.
 * 
 * @param baseUrl The base URL of the Ollama server (default: http://127.0.0.1:11434)
 */
class OllamaClient(
    private val baseUrl: String = "http://127.0.0.1:11434"
) : AutoCloseable {
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    /**
     * Generates embeddings for the given text inputs using the specified model.
     * 
     * @param model The name of the embedding model (e.g., "mxbai-embed-large", "nomic-embed-text")
     * @param input List of text strings to generate embeddings for
     * @return List of embedding vectors (one per input text)
     * @throws Exception if the API request fails
     */
    suspend fun embedText(model: String, input: List<String>): List<List<Float>> {
        logger.debug("Generating embeddings for ${input.size} texts with model: $model")
        
        try {
            val request = EmbedRequest(
                model = model,
                input = input
            )
            
            val response: EmbedResponse = client.post("$baseUrl/api/embed") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            
            logger.info("Successfully generated ${response.embeddings.size} embeddings with model ${response.model}")
            return response.embeddings
        } catch (e: Exception) {
            logger.error("Failed to generate embeddings: ${e.message}", e)
            throw Exception("Failed to generate embeddings from Ollama: ${e.message}", e)
        }
    }
    
    override fun close() {
        logger.debug("Closing Ollama client")
        client.close()
    }
}

