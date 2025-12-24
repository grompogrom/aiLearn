package core.rag

import api.ollama.OllamaClient
import core.config.AppConfig
import core.domain.ChatRequest
import core.domain.Message
import core.domain.MessageRole
import core.provider.LlmProvider
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(RagQueryService::class.java)

/**
 * Exception thrown when RAG index is not found.
 */
class RagIndexNotFoundException : Exception("RAG index not found. Please run /index first to build the index.")

/**
 * Represents a retrieved chunk with similarity score.
 * 
 * @param source The source file name
 * @param text The chunk text content
 * @param similarity The similarity score (0.0 to 1.0) - either cosine or LLM score
 * @param cosineScore The original cosine similarity score (optional, for re-ranking display)
 * @param llmScore The LLM re-ranking score (optional, for re-ranking display)
 */
data class RetrievedChunk(
    val source: String,
    val text: String,
    val similarity: Float,
    val cosineScore: Float? = null,
    val llmScore: Float? = null
)

/**
 * Result of a RAG query.
 * 
 * @param answer The LLM's answer based on retrieved context
 * @param retrievedChunks The chunks retrieved from the index with their similarity scores
 */
data class QueryResult(
    val answer: String,
    val retrievedChunks: List<RetrievedChunk>
)

/**
 * Service for executing RAG queries.
 * Orchestrates the complete pipeline: embed query → search → augment prompt → generate answer.
 * 
 * @param ollamaClient Client for generating embeddings
 * @param llmProvider Provider for generating answers
 * @param config Application configuration for LLM settings
 * @param storage Storage handler for loading indexes
 * @param model The embedding model to use (default: mxbai-embed-large)
 * @param reranker Optional LLM re-ranker for improving retrieval relevance
 */
class RagQueryService(
    private val ollamaClient: OllamaClient,
    private val llmProvider: LlmProvider,
    private val config: AppConfig,
    private val storage: RagStorage = RagStorage(),
    private val model: String = "mxbai-embed-large",
    private val reranker: LlmReranker? = null
) {
    
    /**
     * Executes a RAG query: retrieves relevant chunks and generates an answer.
     * 
     * Pipeline steps:
     * 1. Load index from disk
     * 2. Embed the user's question
     * 3. Find top-K most similar chunks (or more candidates if re-ranking is enabled)
     * 3.5. Re-rank candidates with LLM if enabled
     * 3.6. Filter chunks by threshold (default: 0.7)
     * 4. Format context from retrieved chunks
     * 5. Create augmented prompt
     * 6. Send to LLM for answer generation
     * 7. Return result with answer and retrieved chunks
     * 
     * @param question The user's question
     * @param topK Number of top relevant chunks to retrieve (default: 3)
     * @return QueryResult containing the answer and retrieved chunks
     * @throws RagIndexNotFoundException if index file is not found
     * @throws Exception if Ollama or LLM request fails
     */
    suspend fun query(question: String, topK: Int = 3): QueryResult {
        logger.info("Starting RAG query: \"$question\"")
        logger.debug("Query parameters: topK=$topK, model=$model")
        
        // Step 1: Load index
        logger.debug("Loading RAG index from storage")
        val index = storage.loadIndex()
            ?: throw RagIndexNotFoundException()
        
        logger.info("Loaded index with ${index.chunks.size} chunks (model: ${index.model})")
        
        // Step 2: Embed the question
        logger.debug("Embedding question with Ollama (model: $model)")
        val questionEmbeddings = try {
            ollamaClient.embedText(model, listOf(question))
        } catch (e: Exception) {
            logger.error("Failed to embed question", e)
            throw Exception("Failed to embed question with Ollama: ${e.message}", e)
        }
        
        if (questionEmbeddings.isEmpty()) {
            logger.error("Ollama returned empty embeddings")
            throw Exception("Ollama returned empty embeddings")
        }
        
        val questionEmbedding = questionEmbeddings[0]
        logger.info("Question embedded successfully (dimension: ${questionEmbedding.size})")
        
        // Step 3: Find top-K similar chunks (or more candidates if re-ranking is enabled)
        val candidateCount = if (config.ragReranking && reranker != null) {
            config.ragCandidateCount
        } else {
            topK
        }
        
        logger.debug("Searching for top-$candidateCount similar chunks (re-ranking: ${config.ragReranking})")
        val candidates = SimilaritySearch.findTopK(questionEmbedding, index, candidateCount)
        
        if (candidates.isEmpty()) {
            logger.warn("No relevant chunks found for query")
        } else {
            logger.info("Found ${candidates.size} candidate chunks")
        }
        
        // Step 3.5: Re-rank candidates if enabled
        val rerankedChunks: List<RerankedChunk>? = if (config.ragReranking && reranker != null && candidates.isNotEmpty()) {
            logger.info("Re-ranking ${candidates.size} candidates with LLM")
            try {
                val reranked = reranker.rerank(question, candidates)
                val sorted = reranked.sortedByDescending { it.llmScore }
                logger.info("Re-ranking complete, got ${sorted.size} re-ranked chunks")
                sorted
            } catch (e: Exception) {
                logger.error("Re-ranking failed, falling back to cosine scores", e)
                null
            }
        } else {
            null
        }
        
        // Step 3.6: Filter chunks by threshold and select top-K
        val topChunks: List<Pair<EmbeddedChunk, Float>> = if (rerankedChunks != null) {
            val filtered = rerankedChunks.filter { it.llmScore >= config.ragFilterThreshold.toFloat() }
            logger.info("Filtering: ${rerankedChunks.size} chunks → ${filtered.size} chunks (threshold: ${config.ragFilterThreshold})")
            if (filtered.size < rerankedChunks.size) {
                val rejected = rerankedChunks.size - filtered.size
                logger.debug("Rejected $rejected chunks with score < ${config.ragFilterThreshold}")
            }
            filtered.take(topK).map { Pair(it.chunk, it.llmScore) }
        } else {
            val filtered = candidates.filter { it.second >= config.ragFilterThreshold.toFloat() }
            logger.info("Filtering: ${candidates.size} chunks → ${filtered.size} chunks (threshold: ${config.ragFilterThreshold})")
            if (filtered.size < candidates.size) {
                val rejected = candidates.size - filtered.size
                logger.debug("Rejected $rejected chunks with score < ${config.ragFilterThreshold}")
            }
            filtered.take(topK)
        }
        
        if (topChunks.isEmpty()) {
            logger.warn("No relevant chunks found after filtering")
        } else {
            logger.info("Final selection: ${topChunks.size} chunks")
        }
        
        // Step 4: Format context
        val context = formatContext(topChunks)
        logger.debug("Formatted context (length: ${context.length} chars)")
        
        // Step 5: Create augmented prompt
        val systemMessage = Message(
            role = MessageRole.SYSTEM,
            content = "You are a helpful assistant. Answer the user's question based on the provided context from documents. If the context doesn't contain enough information, say so."
        )
        
        val userMessage = if (topChunks.isEmpty()) {
            // No context found, send question directly
            Message(
                role = MessageRole.USER,
                content = "User Question: $question\n\nNote: No relevant context was found in the knowledge base."
            )
        } else {
            Message(
                role = MessageRole.USER,
                content = "$context\n\nUser Question: $question"
            )
        }
        
        logger.debug("Created augmented prompt with ${if (topChunks.isEmpty()) "no" else topChunks.size} context chunks")
        
        // Step 6: Send to LLM
        logger.info("Sending augmented prompt to LLM")
        val chatRequest = ChatRequest(
            model = config.model,
            messages = listOf(systemMessage, userMessage),
            maxTokens = config.maxTokens,
            temperature = config.temperature
        )
        
        val response = try {
            llmProvider.sendRequest(chatRequest)
        } catch (e: Exception) {
            logger.error("Failed to get response from LLM", e)
            throw Exception("Failed to get response from LLM: ${e.message}", e)
        }
        
        logger.info("Received answer from LLM (length: ${response.content.length} chars)")
        
        // Step 7: Build result
        val retrievedChunks = if (rerankedChunks != null) {
            // Include both cosine and LLM scores when re-ranking was used
            // We need to find original cosine scores for the filtered chunks
            topChunks.map { (chunk, llmScore) ->
                val originalScore = rerankedChunks.find { it.chunk == chunk }?.originalScore ?: llmScore
                RetrievedChunk(
                    source = chunk.source,
                    text = chunk.text,
                    similarity = llmScore,
                    cosineScore = originalScore,
                    llmScore = llmScore
                )
            }
        } else {
            // Only cosine score when re-ranking was not used
            topChunks.map { (chunk, similarity) ->
                RetrievedChunk(
                    source = chunk.source,
                    text = chunk.text,
                    similarity = similarity
                )
            }
        }
        
        logger.info("RAG query completed successfully")
        
        return QueryResult(
            answer = response.content,
            retrievedChunks = retrievedChunks
        )
    }
    
    /**
     * Formats retrieved chunks into a context string for the LLM prompt.
     * 
     * Format:
     * Context from documents:
     * 
     * ---
     * [Source: README.md, Relevance: 0.87]
     * <chunk text>
     * ---
     * 
     * @param chunks List of chunks with their similarity scores
     * @return Formatted context string
     */
    private fun formatContext(chunks: List<Pair<EmbeddedChunk, Float>>): String {
        if (chunks.isEmpty()) {
            return ""
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.append("Context from documents:\n")
        
        for ((chunk, similarity) in chunks) {
            contextBuilder.append("\n---\n")
            contextBuilder.append("[Source: ${chunk.source}, Relevance: ${"%.2f".format(similarity)}]\n")
            contextBuilder.append(chunk.text)
            contextBuilder.append("\n---")
        }
        
        return contextBuilder.toString()
    }
}

