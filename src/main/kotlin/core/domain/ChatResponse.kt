package core.domain

import core.rag.RetrievedChunk

/**
 * Represents a response from the LLM with content and token usage.
 * 
 * @param content The response content from the LLM
 * @param usage Token usage statistics (optional)
 * @param retrievedChunks Optional list of retrieved chunks used for RAG responses
 */
data class ChatResponse(
    val content: String,
    val usage: TokenUsage?,
    val retrievedChunks: List<RetrievedChunk>? = null
)
