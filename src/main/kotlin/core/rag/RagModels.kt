package core.rag

import kotlinx.serialization.Serializable

/**
 * Represents a text chunk from a document.
 * 
 * @param text The text content of the chunk
 * @param source The source file name (e.g., "README.md")
 * @param position The position/index of this chunk in the source document
 */
@Serializable
data class Chunk(
    val text: String,
    val source: String,
    val position: Int
)

/**
 * Represents a text chunk with its embedding vector.
 * 
 * @param text The text content of the chunk
 * @param source The source file name
 * @param position The position/index of this chunk in the source document
 * @param embedding The embedding vector for this chunk
 */
@Serializable
data class EmbeddedChunk(
    val text: String,
    val source: String,
    val position: Int,
    val embedding: List<Float>
)

/**
 * Represents a complete RAG index containing all embedded chunks and metadata.
 * 
 * @param model The embedding model used (e.g., "mxbai-embed-large")
 * @param createdAt ISO timestamp of when the index was created
 * @param chunks List of all embedded chunks
 */
@Serializable
data class RagIndex(
    val model: String,
    val createdAt: String,
    val chunks: List<EmbeddedChunk>
)

