package core.rag

import api.ollama.OllamaClient
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger(IndexingService::class.java)

/**
 * Service for building RAG indexes from documents.
 * 
 * @param ollamaClient Client for generating embeddings
 * @param storage Storage handler for saving/loading indexes
 * @param chunker Document chunker
 * @param model The embedding model to use (default: mxbai-embed-large)
 * @param batchSize Number of chunks to embed at once (default: 10)
 */
class IndexingService(
    private val ollamaClient: OllamaClient,
    private val storage: RagStorage = RagStorage(),
    private val chunker: DocumentChunker = DocumentChunker(),
    private val model: String = "mxbai-embed-large",
    private val batchSize: Int = 10
) {
    
    /**
     * Progress callback for reporting indexing progress.
     * Parameters: current step description
     */
    var progressCallback: ((String) -> Unit)? = null
    
    /**
     * Builds an index from all .md files in the specified directory and its subdirectories.
     *
     * @param sourceDirectory Directory containing .md files to index (default: current working directory)
     * @return The number of chunks indexed
     */
    suspend fun buildIndex(sourceDirectory: String = ""): Int {
        val dirToUse = if (sourceDirectory.isEmpty()) System.getProperty("user.dir") else sourceDirectory
        logger.info("Starting index build from directory: $dirToUse")
        reportProgress("🔍 Scanning for .md files...")

        // 1. Scan directory for .md files
        val documents = loadDocuments(dirToUse)
        
        if (documents.isEmpty()) {
            logger.warn("No .md files found in $dirToUse")
            reportProgress("⚠️ No .md files found in $dirToUse")
            return 0
        }
        
        reportProgress("📚 Found ${documents.size} documents: ${documents.keys.joinToString(", ")}")
        
        // 2. Chunk all documents
        reportProgress("✂️ Splitting documents into chunks...")
        val chunks = chunker.chunkAll(documents)
        
        if (chunks.isEmpty()) {
            logger.warn("No chunks generated from documents")
            reportProgress("⚠️ No chunks generated from documents")
            return 0
        }
        
        reportProgress("📝 Generated ${chunks.size} chunks")
        
        // 3. Generate embeddings in batches
        reportProgress("🧠 Generating embeddings with model: $model...")
        val embeddedChunks = embedChunks(chunks)
        
        reportProgress("✅ Generated ${embeddedChunks.size} embeddings")
        
        // 4. Create and save index
        reportProgress("💾 Saving index...")
        val index = storage.createIndex(embeddedChunks, model)
        storage.saveIndex(index)
        
        reportProgress("✅ Index saved successfully! Total chunks: ${embeddedChunks.size}")
        logger.info("Index build complete. Total chunks: ${embeddedChunks.size}")
        
        return embeddedChunks.size
    }
    
    /**
     * Loads all .md files from a directory and its subdirectories.
     *
     * @param directory Directory to scan
     * @return Map of filename to content
     */
    private fun loadDocuments(directory: String): Map<String, String> {
        val dir = File(directory)

        if (!dir.exists() || !dir.isDirectory) {
            logger.error("Directory does not exist or is not a directory: $directory")
            throw IllegalArgumentException("Invalid directory: $directory")
        }

        val mdFiles = dir.walk().filter {
            it.isFile && it.extension.lowercase() == "md"
        }.toList()

        logger.debug("Found ${mdFiles.size} .md files in $directory and its subdirectories")

        val documents = mutableMapOf<String, String>()

        for (file in mdFiles) {
            try {
                val relativePath = file.relativeTo(dir).toString()
                val content = file.readText()
                if (content.isNotBlank()) {
                    documents[relativePath] = content
                    logger.debug("Loaded $relativePath (${content.length} chars)")
                } else {
                    logger.warn("Skipping empty file: $relativePath")
                }
            } catch (e: Exception) {
                logger.error("Failed to read file: $file.absolutePath", e)
                reportProgress("⚠️ Failed to read $file.name: ${e.message}")
            }
        }

        return documents
    }
    
    /**
     * Generates embeddings for all chunks in batches.
     * 
     * @param chunks List of chunks to embed
     * @return List of embedded chunks
     */
    private suspend fun embedChunks(chunks: List<Chunk>): List<EmbeddedChunk> {
        val embeddedChunks = mutableListOf<EmbeddedChunk>()
        val totalBatches = (chunks.size + batchSize - 1) / batchSize
        
        logger.info("Embedding ${chunks.size} chunks in $totalBatches batches of $batchSize")
        
        chunks.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            val batchNum = batchIndex + 1
            reportProgress("   Processing batch $batchNum/$totalBatches (${batch.size} chunks)...")
            
            try {
                // Extract text from chunks
                val texts = batch.map { it.text }
                
                // Get embeddings from Ollama
                val embeddings = ollamaClient.embedText(model, texts)
                
                // Combine chunks with their embeddings
                batch.zip(embeddings).forEach { (chunk, embedding) ->
                    embeddedChunks.add(
                        EmbeddedChunk(
                            text = chunk.text,
                            source = chunk.source,
                            position = chunk.position,
                            embedding = embedding
                        )
                    )
                }
                
                logger.debug("Batch $batchNum/$totalBatches completed")
            } catch (e: Exception) {
                logger.error("Failed to embed batch $batchNum", e)
                reportProgress("   ⚠️ Failed to embed batch $batchNum: ${e.message}")
                throw Exception("Failed to generate embeddings for batch $batchNum: ${e.message}", e)
            }
        }
        
        return embeddedChunks
    }
    
    /**
     * Reports progress to the callback if set.
     */
    private fun reportProgress(message: String) {
        progressCallback?.invoke(message)
    }
}

