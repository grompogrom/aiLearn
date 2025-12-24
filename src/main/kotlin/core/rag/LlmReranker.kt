package core.rag

import api.ollama.OllamaClient
import core.config.AppConfig
import core.domain.ChatRequest
import core.domain.Message
import core.domain.MessageRole
import core.provider.LlmProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("LlmReranker")

/**
 * Represents a chunk with both original cosine similarity score and LLM re-ranking score.
 * 
 * @param chunk The embedded chunk
 * @param originalScore The original cosine similarity score (0.0 to 1.0)
 * @param llmScore The LLM re-ranking score (0.0 to 1.0)
 */
data class RerankedChunk(
    val chunk: EmbeddedChunk,
    val originalScore: Float,
    val llmScore: Float
)

/**
 * Interface for LLM-based re-ranking of retrieved chunks.
 * Re-ranking improves relevance by using LLM's semantic understanding
 * to evaluate chunks after initial cosine similarity retrieval.
 */
interface LlmReranker {
    /**
     * Re-ranks a list of candidate chunks based on their relevance to the question.
     * 
     * @param question The user's question
     * @param candidates List of chunks with their cosine similarity scores
     * @return List of re-ranked chunks with both original and LLM scores
     */
    suspend fun rerank(
        question: String,
        candidates: List<Pair<EmbeddedChunk, Float>>
    ): List<RerankedChunk>
}

/**
 * Re-ranker implementation using Ollama's generate API.
 * Uses a fast local model for cost-effective re-ranking.
 * 
 * @param ollamaClient The Ollama client for API calls (currently unused, kept for compatibility)
 * @param model The model to use for re-ranking (e.g., "qwen2.5")
 * @param baseUrl The base URL of the Ollama server
 */
class OllamaReranker(
    private val ollamaClient: OllamaClient,
    private val model: String = "qwen2.5",
    private val baseUrl: String = "http://127.0.0.1:11434"
) : LlmReranker {
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        
        // Configure timeouts for re-ranking requests (can be slow)
        engine {
            requestTimeout = 60_000 // 60 seconds for the entire request
            endpoint {
                connectTimeout = 10_000 // 10 seconds to establish connection
                socketTimeout = 60_000 // 60 seconds for socket operations
            }
        }
    }
    
    @Serializable
    data class OllamaGenerateRequest(
        val model: String,
        val prompt: String,
        val stream: Boolean = false
    )
    
    @Serializable
    data class OllamaGenerateResponse(
        val model: String,
        val created_at: String,
        val response: String,
        val done: Boolean,
        val done_reason: String? = null,
        val context: List<Int>? = null,
        val total_duration: Long? = null,
        val load_duration: Long? = null,
        val prompt_eval_count: Int? = null,
        val prompt_eval_duration: Long? = null,
        val eval_count: Int? = null,
        val eval_duration: Long? = null
    )
    
    @Serializable
    data class RerankScore(
        val id: Int,
        val score: Double
    )
    
    override suspend fun rerank(
        question: String,
        candidates: List<Pair<EmbeddedChunk, Float>>
    ): List<RerankedChunk> {
        logger.info("Re-ranking ${candidates.size} candidates with Ollama model: $model")
        
        if (candidates.isEmpty()) {
            logger.warn("No candidates to re-rank")
            return emptyList()
        }
        
        try {
            // Build prompt with truncated chunks
            val prompt = buildRerankPrompt(question, candidates)
            logger.debug("Built re-ranking prompt (length: ${prompt.length} chars)")
            
            // Call Ollama generate API with proper format
            val request = OllamaGenerateRequest(
                model = model,
                prompt = prompt,
                stream = false
            )
            
            logger.debug("Sending request to Ollama: $baseUrl/api/generate")
            logger.debug("Request: model=$model, stream=false")
            logger.info("⏳ Waiting for Ollama to re-rank candidates (this may take 30-60 seconds)...")
            
            val startTime = System.currentTimeMillis()
            val httpResponse = client.post("$baseUrl/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            val elapsedTime = System.currentTimeMillis() - startTime
            
            logger.info("✓ Ollama responded in ${elapsedTime}ms (${elapsedTime / 1000}s)")
            logger.debug("Response status: ${httpResponse.status.value} ${httpResponse.status.description}")
            logger.debug("Response Content-Type: ${httpResponse.contentType()}")
            
            // Read response as text (Ollama returns application/x-ndjson even with stream=false)
            val rawResponse = httpResponse.bodyAsText()
            logger.debug("Raw response length: ${rawResponse.length} chars")
            logger.debug("Raw response preview: ${rawResponse.take(300)}")
            
            // Parse JSON response
            // Ollama streams even with stream=false, sending multiple JSON objects (one per token)
            // We need to concatenate all response fields from each line
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val lines = rawResponse.lines().filter { it.isNotBlank() }
            
            val completeResponse = StringBuilder()
            lines.forEach { line ->
                try {
                    val obj = json.decodeFromString<OllamaGenerateResponse>(line)
                    completeResponse.append(obj.response)
                } catch (e: Exception) {
                    logger.warn("Failed to parse line: ${line.take(100)}, error: ${e.message}")
                }
            }
            
            // Create a synthetic response object with the complete concatenated response
            val response = OllamaGenerateResponse(
                model = "qwen2.5",
                created_at = java.time.Instant.now().toString(),
                response = completeResponse.toString(),
                done = true
            )
            
            logger.debug("Ollama response done: ${response.done}")
            logger.debug("Received response content (length: ${response.response.length})")
            logger.debug("Response preview: ${response.response.take(300)}")
            
            // Parse JSON scores from the response
            val scores = parseScores(response.response, candidates.size)
            logger.debug("Parsed scores map: $scores")
            
            // Map scores to chunks
            return mapScoresToChunks(candidates, scores)
            
        } catch (e: Exception) {
            logger.error("Failed to re-rank with Ollama: ${e.message}", e)
            logger.error("Stack trace:", e)
            // Fallback: return original scores
            return candidates.map { (chunk, score) ->
                RerankedChunk(chunk, score, score)
            }
        }
    }
    
    private fun buildRerankPrompt(
        question: String,
        candidates: List<Pair<EmbeddedChunk, Float>>
    ): String {
        val sb = StringBuilder()
        sb.append("Rate text chunks by relevance to the question. Return ONLY JSON array.\n\n")
        sb.append("Question: $question\n\n")
        sb.append("Chunks:\n")
        
        candidates.forEachIndexed { index, (chunk, _) ->
            // Truncate chunk text to max 200 chars for faster processing
            val truncatedText = if (chunk.text.length > 200) {
                chunk.text.take(197) + "..."
            } else {
                chunk.text
            }
            sb.append("${index + 1}. $truncatedText\n")
        }
        
        sb.append("\nRate 0.0-1.0. Return JSON: [{\"id\":1,\"score\":0.9},{\"id\":2,\"score\":0.5},...]\nJSON:")
        
        return sb.toString()
    }
    
    private fun parseScores(jsonResponse: String, expectedCount: Int): Map<Int, Float> {
        return try {
            logger.debug("Attempting to parse JSON response (length: ${jsonResponse.length})")
            logger.debug("Response content: ${jsonResponse.take(500)}")
            
            // Try to extract JSON array - Ollama may add text before/after
            val jsonStart = jsonResponse.indexOf('[')
            val jsonEnd = jsonResponse.lastIndexOf(']') + 1
            
            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                logger.error("No JSON array found in response")
                logger.error("Full response: $jsonResponse")
                throw Exception("No JSON array found in response")
            }
            
            val jsonArray = jsonResponse.substring(jsonStart, jsonEnd)
            logger.debug("Extracted JSON array (length: ${jsonArray.length}): $jsonArray")
            
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val scores = json.decodeFromString<List<RerankScore>>(jsonArray)
            
            logger.info("Successfully parsed ${scores.size} re-ranking scores from Ollama")
            scores.forEach { logger.debug("  Chunk ID: ${it.id}, Score: ${it.score}") }
            
            // Validate scores
            if (scores.size != expectedCount) {
                logger.warn("Expected $expectedCount scores, but got ${scores.size}")
            }
            
            val scoreMap = scores.associate { it.id to it.score.toFloat() }
            logger.debug("Created score map with ${scoreMap.size} entries")
            
            scoreMap
        } catch (e: Exception) {
            logger.error("Failed to parse JSON scores: ${e.message}", e)
            logger.error("Response was: ${jsonResponse.take(1000)}")
            // Fallback: return uniform scores
            val fallbackMap = (1..expectedCount).associateWith { 0.5f }
            logger.warn("Using fallback uniform scores (0.5) for all $expectedCount candidates")
            fallbackMap
        }
    }
    
    private fun mapScoresToChunks(
        candidates: List<Pair<EmbeddedChunk, Float>>,
        scores: Map<Int, Float>
    ): List<RerankedChunk> {
        logger.debug("Mapping ${scores.size} scores to ${candidates.size} candidates")
        return candidates.mapIndexed { index, (chunk, originalScore) ->
            val llmScore = scores[index + 1] ?: run {
                logger.warn("No LLM score found for index ${index + 1}, using original score $originalScore")
                originalScore
            }
            logger.debug("Chunk ${index + 1}: originalScore=$originalScore, llmScore=$llmScore")
            RerankedChunk(chunk, originalScore, llmScore)
        }
    }
    
    fun close() {
        client.close()
    }
}

/**
 * Re-ranker implementation using the existing LlmProvider (e.g., Perplexity).
 * Provides higher quality re-ranking but at higher cost.
 * 
 * @param llmProvider The LLM provider for API calls
 * @param config Application configuration
 */
class ProviderReranker(
    private val llmProvider: LlmProvider,
    private val config: AppConfig
) : LlmReranker {
    
    @Serializable
    data class RerankScore(
        val id: Int,
        val score: Double
    )
    
    override suspend fun rerank(
        question: String,
        candidates: List<Pair<EmbeddedChunk, Float>>
    ): List<RerankedChunk> {
        logger.info("Re-ranking ${candidates.size} candidates with LlmProvider")
        
        if (candidates.isEmpty()) {
            logger.warn("No candidates to re-rank")
            return emptyList()
        }
        
        try {
            // Build prompt with truncated chunks
            val prompt = buildRerankPrompt(question, candidates)
            logger.debug("Built re-ranking prompt (length: ${prompt.length} chars)")
            
            // Create chat request
            val systemMessage = Message(
                role = MessageRole.SYSTEM,
                content = "You are a relevance evaluation assistant. Return ONLY valid JSON, no explanations."
            )
            
            val userMessage = Message(
                role = MessageRole.USER,
                content = prompt
            )
            
            val chatRequest = ChatRequest(
                model = config.model,
                messages = listOf(systemMessage, userMessage),
                maxTokens = 1000,
                temperature = 0.1
            )
            
            val response = llmProvider.sendRequest(chatRequest)
            val responseContent = response.content
            logger.debug("Received response from LlmProvider: ${responseContent.take(200)}")
            logger.debug("Full response length: ${responseContent.length} chars")
            
            // Parse JSON scores
            val scores = parseScores(responseContent, candidates.size)
            logger.debug("Parsed scores map: $scores")
            
            // Map scores to chunks
            return mapScoresToChunks(candidates, scores)
            
        } catch (e: Exception) {
            logger.error("Failed to re-rank with LlmProvider, falling back to original scores", e)
            // Fallback: return original scores
            return candidates.map { (chunk, score) ->
                RerankedChunk(chunk, score, score)
            }
        }
    }
    
    private fun buildRerankPrompt(
        question: String,
        candidates: List<Pair<EmbeddedChunk, Float>>
    ): String {
        val sb = StringBuilder()
        sb.append("Given the user question and list of text chunks, evaluate each chunk's relevance.\n")
        sb.append("Rate each chunk from 0.0 (irrelevant) to 1.0 (highly relevant).\n\n")
        sb.append("Question: $question\n\n")
        sb.append("Chunks:\n")
        
        candidates.forEachIndexed { index, (chunk, _) ->
            // Truncate chunk text to max 200 chars
            val truncatedText = if (chunk.text.length > 200) {
                chunk.text.take(197) + "..."
            } else {
                chunk.text
            }
            sb.append("#${index + 1}: $truncatedText\n\n")
        }
        
        sb.append("Return ONLY valid JSON array: [{\"id\": 1, \"score\": 0.85}, {\"id\": 2, \"score\": 0.42}, ...]")
        
        return sb.toString()
    }
    
    private fun parseScores(jsonResponse: String, expectedCount: Int): Map<Int, Float> {
        return try {
            logger.debug("Attempting to parse JSON response: $jsonResponse")
            
            // Try to extract JSON array from response (may have extra text)
            val jsonStart = jsonResponse.indexOf('[')
            val jsonEnd = jsonResponse.lastIndexOf(']') + 1
            
            if (jsonStart == -1 || jsonEnd == 0) {
                logger.error("No JSON array found in response")
                throw Exception("No JSON array found in response")
            }
            
            val jsonArray = jsonResponse.substring(jsonStart, jsonEnd)
            logger.debug("Extracted JSON array: $jsonArray")
            
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val scores = json.decodeFromString<List<RerankScore>>(jsonArray)
            
            logger.info("Successfully parsed ${scores.size} re-ranking scores from LLM")
            scores.forEach { logger.debug("  ID: ${it.id}, Score: ${it.score}") }
            
            val scoreMap = scores.associate { it.id to it.score.toFloat() }
            logger.debug("Created score map with ${scoreMap.size} entries: $scoreMap")
            
            scoreMap
        } catch (e: Exception) {
            logger.error("Failed to parse JSON scores, using uniform fallback. Error: ${e.message}", e)
            logger.error("JSON response was: $jsonResponse")
            // Fallback: return uniform scores
            val fallbackMap = (1..expectedCount).associateWith { 0.5f }
            logger.warn("Using fallback uniform scores: $fallbackMap")
            fallbackMap
        }
    }
    
    private fun mapScoresToChunks(
        candidates: List<Pair<EmbeddedChunk, Float>>,
        scores: Map<Int, Float>
    ): List<RerankedChunk> {
        logger.debug("Mapping ${scores.size} scores to ${candidates.size} candidates")
        return candidates.mapIndexed { index, (chunk, originalScore) ->
            val llmScore = scores[index + 1] ?: run {
                logger.warn("No LLM score found for index ${index + 1}, using original score $originalScore")
                originalScore
            }
            logger.debug("Chunk ${index + 1}: originalScore=$originalScore, llmScore=$llmScore")
            RerankedChunk(chunk, originalScore, llmScore)
        }
    }
}

