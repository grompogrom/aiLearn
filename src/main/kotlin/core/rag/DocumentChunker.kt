package core.rag

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DocumentChunker::class.java)

/**
 * Splits documents into overlapping text chunks.
 * 
 * @param chunkSize Maximum size of each chunk in characters
 * @param overlap Number of characters to overlap between consecutive chunks
 */
class DocumentChunker(
    private val chunkSize: Int = 500,
    private val overlap: Int = 50
) {
    init {
        require(chunkSize > overlap) { "Chunk size must be greater than overlap" }
        require(overlap >= 0) { "Overlap must be non-negative" }
    }
    
    /**
     * Chunks a document into overlapping text segments.
     * 
     * @param content The full text content to chunk
     * @param source The source file name
     * @return List of chunks with metadata
     */
    fun chunk(content: String, source: String): List<Chunk> {
        if (content.isBlank()) {
            logger.warn("Received blank content for source: $source")
            return emptyList()
        }
        
        val chunks = mutableListOf<Chunk>()
        val stepSize = chunkSize - overlap
        var position = 0
        var start = 0
        
        while (start < content.length) {
            // Calculate end position for this chunk
            val end = minOf(start + chunkSize, content.length)
            
            // Extract chunk text
            var chunkText = content.substring(start, end)
            
            // If this is not the last chunk and we're not at the end,
            // try to break at a word boundary to avoid splitting words
            if (end < content.length && end - start == chunkSize) {
                val lastSpace = chunkText.lastIndexOf(' ')
                if (lastSpace > chunkSize / 2) { // Only adjust if space is in latter half
                    chunkText = chunkText.substring(0, lastSpace)
                }
            }
            
            // Clean up the chunk text
            val cleanedText = chunkText.trim()
            
            if (cleanedText.isNotEmpty()) {
                chunks.add(Chunk(
                    text = cleanedText,
                    source = source,
                    position = position
                ))
                position++
            }
            
            // Move to next chunk position
            start += stepSize
            
            // Prevent infinite loop if stepSize is too small
            if (stepSize <= 0) {
                logger.error("Invalid stepSize: $stepSize (chunkSize: $chunkSize, overlap: $overlap)")
                break
            }
        }
        
        logger.debug("Chunked $source into ${chunks.size} chunks (chunkSize: $chunkSize, overlap: $overlap)")
        return chunks
    }
    
    /**
     * Chunks multiple documents.
     * 
     * @param documents Map of source file names to their content
     * @return List of all chunks from all documents
     */
    fun chunkAll(documents: Map<String, String>): List<Chunk> {
        logger.info("Chunking ${documents.size} documents")
        val allChunks = documents.flatMap { (source, content) ->
            chunk(content, source)
        }
        logger.info("Generated ${allChunks.size} total chunks from ${documents.size} documents")
        return allChunks
    }
}

