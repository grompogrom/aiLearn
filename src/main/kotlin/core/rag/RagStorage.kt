package core.rag

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

private val logger = LoggerFactory.getLogger(RagStorage::class.java)

/**
 * Handles storage and retrieval of RAG indexes.
 * 
 * @param indexDirectory The directory where indexes are stored (default: dataForRag/indexed)
 */
class RagStorage(
    private val indexDirectory: String = "dataForRag/indexed"
) {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Saves a RAG index to disk as JSON.
     * 
     * @param index The RAG index to save
     * @param filename The filename to save to (default: index.json)
     */
    fun saveIndex(index: RagIndex, filename: String = "index.json") {
        logger.info("Saving RAG index with ${index.chunks.size} chunks to $indexDirectory/$filename")
        
        try {
            // Create directory if it doesn't exist
            val dir = File(indexDirectory)
            if (!dir.exists()) {
                logger.debug("Creating index directory: $indexDirectory")
                dir.mkdirs()
            }
            
            // Serialize index to JSON
            val jsonString = json.encodeToString(index)
            
            // Write to file
            val file = File(dir, filename)
            file.writeText(jsonString)
            
            logger.info("Successfully saved RAG index to ${file.absolutePath}")
            logger.debug("Index size: ${file.length()} bytes")
        } catch (e: Exception) {
            logger.error("Failed to save RAG index", e)
            throw Exception("Failed to save RAG index: ${e.message}", e)
        }
    }
    
    /**
     * Loads a RAG index from disk.
     * 
     * @param filename The filename to load from (default: index.json)
     * @return The loaded RAG index, or null if the file doesn't exist
     */
    fun loadIndex(filename: String = "index.json"): RagIndex? {
        val file = File(indexDirectory, filename)
        
        if (!file.exists()) {
            logger.warn("Index file not found: ${file.absolutePath}")
            return null
        }
        
        return try {
            logger.debug("Loading RAG index from ${file.absolutePath}")
            val jsonString = file.readText()
            val index = json.decodeFromString<RagIndex>(jsonString)
            logger.info("Successfully loaded RAG index with ${index.chunks.size} chunks")
            index
        } catch (e: Exception) {
            logger.error("Failed to load RAG index", e)
            throw Exception("Failed to load RAG index: ${e.message}", e)
        }
    }
    
    /**
     * Creates a RAG index from embedded chunks.
     * 
     * @param embeddedChunks List of embedded chunks
     * @param model The embedding model used
     * @return A new RAG index with current timestamp
     */
    fun createIndex(embeddedChunks: List<EmbeddedChunk>, model: String): RagIndex {
        return RagIndex(
            model = model,
            createdAt = Instant.now().toString(),
            chunks = embeddedChunks
        )
    }
}

