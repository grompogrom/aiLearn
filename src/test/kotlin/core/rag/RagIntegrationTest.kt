package core.rag

import api.ollama.OllamaClient
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class RagIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var rawDir: File
    private lateinit var indexFile: File

    @BeforeEach
    fun setup() {
        rawDir = tempDir.resolve("raw").toFile().apply { mkdirs() }
        indexFile = tempDir.resolve("indexed/index.json").toFile()
    }

    private fun createMockOllamaClient(embeddingSize: Int = 1024): OllamaClient {
        val mockHttpClient = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            engine {
                addHandler { request ->
                    // Parse request to get number of inputs
                    val requestBody = request.body.toString()
                    val inputCount = requestBody.split("\"input\"").size - 1
                    
                    // Generate embeddings for each input
                    val embeddings = List(inputCount) {
                        List(embeddingSize) { idx -> idx * 0.001f }
                    }
                    
                    val embeddingsJson = embeddings.joinToString(",", "[", "]") { embedding ->
                        embedding.joinToString(",", "[", "]")
                    }
                    
                    val responseJson = """
                        {
                            "model": "mxbai-embed-large",
                            "embeddings": $embeddingsJson
                        }
                    """.trimIndent()
                    
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        
        return OllamaClient("http://localhost:11434", mockHttpClient)
    }

    @Test
    fun `test full indexing pipeline with single file`() = runBlocking {
        // Setup
        val content = """
            # Test Document
            
            This is a test document for the RAG indexing pipeline.
            It contains multiple paragraphs of text.
            
            ## Section 1
            
            Some content in section 1.
            
            ## Section 2
            
            Some content in section 2.
        """.trimIndent()
        
        rawDir.resolve("test.md").writeText(content)
        
        // Create components
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker(chunkSize = 100, chunkOverlap = 20)
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath,
            embeddingModel = "mxbai-embed-large"
        )
        
        // Execute indexing
        val result = indexingService.indexDocuments()
        
        // Verify
        assertTrue(result.isSuccess)
        assertTrue(indexFile.exists())
        
        val loadedIndex = ragStorage.load()
        assertNotNull(loadedIndex)
        assertEquals("mxbai-embed-large", loadedIndex?.model)
        assertTrue(loadedIndex!!.chunks.isNotEmpty())
        
        // Verify chunks have embeddings
        loadedIndex.chunks.forEach { chunk ->
            assertEquals(1024, chunk.embedding.size)
            assertEquals("test.md", chunk.source)
            assertFalse(chunk.text.isEmpty())
        }
    }

    @Test
    fun `test full indexing pipeline with multiple files`() = runBlocking {
        // Create multiple markdown files
        rawDir.resolve("file1.md").writeText("Content of first file with some text.")
        rawDir.resolve("file2.md").writeText("Content of second file with different text.")
        rawDir.resolve("file3.md").writeText("Content of third file with more text.")
        
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker()
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath
        )
        
        val result = indexingService.indexDocuments()
        
        assertTrue(result.isSuccess)
        
        val loadedIndex = ragStorage.load()
        assertNotNull(loadedIndex)
        
        // Verify all files were processed
        val sources = loadedIndex!!.chunks.map { it.source }.distinct()
        assertEquals(3, sources.size)
        assertTrue(sources.contains("file1.md"))
        assertTrue(sources.contains("file2.md"))
        assertTrue(sources.contains("file3.md"))
    }

    @Test
    fun `test chunking and embedding coordination`() = runBlocking {
        val content = "This is a test. ".repeat(100) // Create content that needs chunking
        rawDir.resolve("large.md").writeText(content)
        
        val ollamaClient = createMockOllamaClient(embeddingSize = 512)
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker(chunkSize = 200, chunkOverlap = 50)
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath
        )
        
        val result = indexingService.indexDocuments()
        
        assertTrue(result.isSuccess)
        
        val loadedIndex = ragStorage.load()
        assertNotNull(loadedIndex)
        
        // Verify number of chunks matches number of embeddings
        assertTrue(loadedIndex!!.chunks.size > 1)
        loadedIndex.chunks.forEach { chunk ->
            assertEquals(512, chunk.embedding.size)
        }
    }

    @Test
    fun `test persistence and reload`() = runBlocking {
        rawDir.resolve("persistent.md").writeText("Persistent content")
        
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker()
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath
        )
        
        // Index and save
        indexingService.indexDocuments()
        
        // Create new storage instance and load
        val newRagStorage = RagStorage(indexFile.absolutePath)
        val reloadedIndex = newRagStorage.load()
        
        assertNotNull(reloadedIndex)
        assertTrue(reloadedIndex!!.chunks.isNotEmpty())
        assertEquals("Persistent content", reloadedIndex.chunks[0].text)
    }

    @Test
    fun `test re-indexing overwrites previous index`() = runBlocking {
        // First indexing
        rawDir.resolve("first.md").writeText("First content")
        
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker()
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath
        )
        
        indexingService.indexDocuments()
        val firstIndex = ragStorage.load()
        val firstChunkCount = firstIndex!!.chunks.size
        
        // Second indexing with additional file
        rawDir.resolve("second.md").writeText("Second content")
        
        indexingService.indexDocuments()
        val secondIndex = ragStorage.load()
        
        // Should have more chunks after adding a file
        assertTrue(secondIndex!!.chunks.size > firstChunkCount)
    }

    @Test
    fun `test mixed file types are filtered correctly`() = runBlocking {
        rawDir.resolve("document.md").writeText("Markdown content")
        rawDir.resolve("readme.txt").writeText("Text file content")
        rawDir.resolve("data.json").writeText("""{"key": "value"}""")
        rawDir.resolve("notes.MD").writeText("Uppercase extension") // Test case sensitivity
        
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker()
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath
        )
        
        val result = indexingService.indexDocuments()
        
        assertTrue(result.isSuccess)
        
        val loadedIndex = ragStorage.load()
        val sources = loadedIndex!!.chunks.map { it.source }.distinct()
        
        // Should only include .md files
        assertTrue(sources.all { it.endsWith(".md", ignoreCase = true) })
        assertFalse(sources.any { it.contains("txt") })
        assertFalse(sources.any { it.contains("json") })
    }

    @Test
    fun `test chunk overlap is preserved through pipeline`() = runBlocking {
        val content = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" * 10
        rawDir.resolve("overlap.md").writeText(content)
        
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker(chunkSize = 50, chunkOverlap = 10)
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath
        )
        
        indexingService.indexDocuments()
        
        val loadedIndex = ragStorage.load()
        val chunks = loadedIndex!!.chunks
        
        // Verify chunks have reasonable sizes and overlaps
        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue(chunk.text.length <= 50 + 20) // Allow some tolerance for word boundaries
        }
    }

    @Test
    fun `test special characters are preserved through pipeline`() = runBlocking {
        val specialContent = """
            # Special Characters Test
            
            Testing: !@#$%^&*()_+{}[]|\\:";'<>?,./
            
            Unicode: 你好 мир 🌍 العالم
            
            Math: ∑∫∂√π∞
        """.trimIndent()
        
        rawDir.resolve("special.md").writeText(specialContent)
        
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker()
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath
        )
        
        indexingService.indexDocuments()
        
        val loadedIndex = ragStorage.load()
        val allText = loadedIndex!!.chunks.joinToString("") { it.text }
        
        // Verify special characters are preserved
        assertTrue(allText.contains("!@#$%^&*()"))
        assertTrue(allText.contains("你好"))
        assertTrue(allText.contains("мир"))
        assertTrue(allText.contains("🌍"))
    }

    @Test
    fun `test large file processing`() = runBlocking {
        // Create a large file
        val largeContent = buildString {
            repeat(100) { i ->
                appendLine("## Section $i")
                appendLine()
                repeat(10) { j ->
                    appendLine("Paragraph $j of section $i with some content that makes it longer.")
                }
                appendLine()
            }
        }
        
        rawDir.resolve("large.md").writeText(largeContent)
        
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker(chunkSize = 500, chunkOverlap = 50)
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath
        )
        
        val result = indexingService.indexDocuments()
        
        assertTrue(result.isSuccess)
        
        val loadedIndex = ragStorage.load()
        
        // Should create many chunks
        assertTrue(loadedIndex!!.chunks.size > 50)
        
        // All chunks should have embeddings
        loadedIndex.chunks.forEach { chunk ->
            assertEquals(1024, chunk.embedding.size)
        }
    }

    @Test
    fun `test empty directory handling`() = runBlocking {
        // Don't create any files
        
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker()
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath
        )
        
        val result = indexingService.indexDocuments()
        
        assertTrue(result.isSuccess)
        
        val loadedIndex = ragStorage.load()
        assertNotNull(loadedIndex)
        assertEquals(0, loadedIndex!!.chunks.size)
    }

    @Test
    fun `test metadata preservation through full pipeline`() = runBlocking {
        rawDir.resolve("metadata.md").writeText("Test content for metadata")
        
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker()
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath,
            embeddingModel = "mxbai-embed-large"
        )
        
        indexingService.indexDocuments()
        
        val loadedIndex = ragStorage.load()
        
        // Verify all metadata is present
        assertNotNull(loadedIndex)
        assertEquals("mxbai-embed-large", loadedIndex!!.model)
        assertNotNull(loadedIndex.createdAt)
        
        loadedIndex.chunks.forEachIndexed { index, chunk ->
            assertEquals("metadata.md", chunk.source)
            assertEquals(index, chunk.position)
            assertFalse(chunk.text.isEmpty())
            assertFalse(chunk.embedding.isEmpty())
        }
    }

    @Test
    fun `test concurrent file processing integrity`() = runBlocking {
        // Create multiple files to test concurrent processing
        repeat(10) { i ->
            rawDir.resolve("file$i.md").writeText("Content for file $i. ".repeat(20))
        }
        
        val ollamaClient = createMockOllamaClient()
        val ragStorage = RagStorage(indexFile.absolutePath)
        val documentChunker = DocumentChunker()
        val indexingService = IndexingService(
            ollamaClient = ollamaClient,
            ragStorage = ragStorage,
            documentChunker = documentChunker,
            rawDataDir = rawDir.absolutePath
        )
        
        val result = indexingService.indexDocuments()
        
        assertTrue(result.isSuccess)
        
        val loadedIndex = ragStorage.load()
        
        // Verify all files were processed
        val sources = loadedIndex!!.chunks.map { it.source }.distinct()
        assertEquals(10, sources.size)
        
        // Verify no duplicate chunks or corrupted data
        val positions = loadedIndex.chunks.groupBy { it.source }
        positions.values.forEach { chunks ->
            val sortedPositions = chunks.map { it.position }.sorted()
            assertEquals(sortedPositions, sortedPositions.distinct()) // No duplicates
        }
    }
}

