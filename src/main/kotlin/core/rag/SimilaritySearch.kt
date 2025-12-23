package core.rag

import org.slf4j.LoggerFactory
import kotlin.math.sqrt

private val logger = LoggerFactory.getLogger(SimilaritySearch::class.java)

/**
 * Provides similarity search functionality for RAG queries.
 * Uses cosine similarity to find the most relevant chunks for a given query embedding.
 */
object SimilaritySearch {
    
    /**
     * Calculates cosine similarity between two vectors.
     * 
     * Cosine similarity = dot(A, B) / (norm(A) * norm(B))
     * Returns a value between -1 and 1, where 1 means identical direction.
     * 
     * @param vec1 First vector
     * @param vec2 Second vector
     * @return Cosine similarity score
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    fun cosineSimilarity(vec1: List<Float>, vec2: List<Float>): Float {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Vectors must have the same dimension: ${vec1.size} != ${vec2.size}")
        }
        
        if (vec1.isEmpty()) {
            throw IllegalArgumentException("Vectors cannot be empty")
        }
        
        // Calculate dot product
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        // Calculate norms
        val magnitude1 = sqrt(norm1)
        val magnitude2 = sqrt(norm2)
        
        // Avoid division by zero
        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            logger.warn("One or both vectors have zero magnitude")
            return 0.0f
        }
        
        val similarity = (dotProduct / (magnitude1 * magnitude2)).toFloat()
        
        logger.debug("Calculated cosine similarity: $similarity (dim: ${vec1.size})")
        
        return similarity
    }
    
    /**
     * Finds the top-K most similar chunks to a query embedding.
     * 
     * @param queryEmbedding The embedding vector of the user's query
     * @param index The RAG index containing all embedded chunks
     * @param k Number of top results to return (default: 3)
     * @return List of pairs containing the chunk and its similarity score, sorted by score descending
     */
    fun findTopK(
        queryEmbedding: List<Float>,
        index: RagIndex,
        k: Int = 3
    ): List<Pair<EmbeddedChunk, Float>> {
        logger.info("Finding top-$k similar chunks from ${index.chunks.size} total chunks")
        
        if (index.chunks.isEmpty()) {
            logger.warn("Index contains no chunks")
            return emptyList()
        }
        
        if (k <= 0) {
            logger.warn("Invalid k value: $k, returning empty list")
            return emptyList()
        }
        
        // Calculate similarity for each chunk
        val similarities = mutableListOf<Pair<EmbeddedChunk, Float>>()
        
        for (chunk in index.chunks) {
            try {
                val similarity = cosineSimilarity(queryEmbedding, chunk.embedding)
                similarities.add(Pair(chunk, similarity))
                
                logger.debug("Chunk from ${chunk.source} (pos ${chunk.position}): similarity = $similarity")
            } catch (e: Exception) {
                logger.error("Failed to calculate similarity for chunk from ${chunk.source}", e)
                // Skip this chunk and continue
            }
        }
        
        // Sort by similarity descending and take top-K
        val topK = similarities
            .sortedByDescending { it.second }
            .take(k)
        
        logger.info("Top-$k results:")
        topK.forEachIndexed { index, (chunk, score) ->
            logger.info("  ${index + 1}. [${chunk.source}] score: ${"%.4f".format(score)}")
        }
        
        return topK
    }
}

