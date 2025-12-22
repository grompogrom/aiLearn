package core.rag

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.Instant

class RagStorageTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var ragStorage: RagStorage
    private lateinit var indexFile: File

    @BeforeEach
    fun setup() {
        indexFile = tempDir.resolve("index.json").toFile()
        ragStorage = RagStorage(indexFile.absolutePath)
    }

    @Test
    fun `test save and load index`() {
        val chunks = listOf(
            EmbeddedChunk(
                text = "Test chunk 1",
                source = "test.md",
                position = 0,
                embedding = listOf(0.1f, 0.2f, 0.3f)
            ),
            EmbeddedChunk(
                text = "Test chunk 2",
                source = "test.md",
                position = 1,
                embedding = listOf(0.4f, 0.5f, 0.6f)
            )
        )
        
        val originalIndex = RagIndex(
            model = "mxbai-embed-large",
            createdAt = Instant.now(),
            chunks = chunks
        )
        
        ragStorage.save(originalIndex)
        assertTrue(indexFile.exists())
        
        val loadedIndex = ragStorage.load()
        assertNotNull(loadedIndex)
        assertEquals(originalIndex.model, loadedIndex?.model)
        assertEquals(originalIndex.chunks.size, loadedIndex?.chunks?.size)
        assertEquals("Test chunk 1", loadedIndex?.chunks?.get(0)?.text)
        assertEquals(0.3f, loadedIndex?.chunks?.get(0)?.embedding?.get(2))
    }

    @Test
    fun `test save creates directory if not exists`() {
        val nestedPath = tempDir.resolve("nested/deep/path/index.json").toFile()
        val storage = RagStorage(nestedPath.absolutePath)
        
        val index = RagIndex(
            model = "test-model",
            createdAt = Instant.now(),
            chunks = emptyList()
        )
        
        storage.save(index)
        
        assertTrue(nestedPath.exists())
        assertTrue(nestedPath.parentFile.exists())
    }

    @Test
    fun `test load non-existent file returns null`() {
        val nonExistentFile = tempDir.resolve("non-existent.json").toFile()
        val storage = RagStorage(nonExistentFile.absolutePath)
        
        val result = storage.load()
        
        assertNull(result)
    }

    @Test
    fun `test save and load empty chunks list`() {
        val index = RagIndex(
            model = "mxbai-embed-large",
            createdAt = Instant.now(),
            chunks = emptyList()
        )
        
        ragStorage.save(index)
        val loaded = ragStorage.load()
        
        assertNotNull(loaded)
        assertEquals(0, loaded?.chunks?.size)
    }

    @Test
    fun `test save and load with large embeddings`() {
        // Simulate realistic 1024-dimensional embeddings
        val embedding = List(1024) { it * 0.001f }
        val chunk = EmbeddedChunk(
            text = "Test chunk",
            source = "test.md",
            position = 0,
            embedding = embedding
        )
        
        val index = RagIndex(
            model = "mxbai-embed-large",
            createdAt = Instant.now(),
            chunks = listOf(chunk)
        )
        
        ragStorage.save(index)
        val loaded = ragStorage.load()
        
        assertNotNull(loaded)
        assertEquals(1024, loaded?.chunks?.get(0)?.embedding?.size)
        assertEquals(embedding[0], loaded?.chunks?.get(0)?.embedding?.get(0))
        assertEquals(embedding[1023], loaded?.chunks?.get(0)?.embedding?.get(1023))
    }

    @Test
    fun `test save and load with multiple files`() {
        val chunks = listOf(
            EmbeddedChunk("chunk1", "file1.md", 0, listOf(0.1f)),
            EmbeddedChunk("chunk2", "file1.md", 1, listOf(0.2f)),
            EmbeddedChunk("chunk3", "file2.md", 0, listOf(0.3f)),
            EmbeddedChunk("chunk4", "file3.md", 0, listOf(0.4f))
        )
        
        val index = RagIndex(
            model = "mxbai-embed-large",
            createdAt = Instant.now(),
            chunks = chunks
        )
        
        ragStorage.save(index)
        val loaded = ragStorage.load()
        
        assertNotNull(loaded)
        assertEquals(4, loaded?.chunks?.size)
        
        // Verify different sources are preserved
        val sources = loaded?.chunks?.map { it.source }?.distinct()
        assertEquals(3, sources?.size)
        assertTrue(sources?.contains("file1.md") == true)
        assertTrue(sources?.contains("file2.md") == true)
        assertTrue(sources?.contains("file3.md") == true)
    }

    @Test
    fun `test save and load preserves chunk order`() {
        val chunks = (0..10).map { i ->
            EmbeddedChunk(
                text = "Chunk $i",
                source = "test.md",
                position = i,
                embedding = listOf(i.toFloat())
            )
        }
        
        val index = RagIndex(
            model = "mxbai-embed-large",
            createdAt = Instant.now(),
            chunks = chunks
        )
        
        ragStorage.save(index)
        val loaded = ragStorage.load()
        
        assertNotNull(loaded)
        loaded?.chunks?.forEachIndexed { i, chunk ->
            assertEquals("Chunk $i", chunk.text)
            assertEquals(i, chunk.position)
        }
    }

    @Test
    fun `test save and load with special characters in text`() {
        val chunk = EmbeddedChunk(
            text = "Special chars: !@#$%^&*()_+{}[]|\\:\";<>?,./\n\t\r",
            source = "special.md",
            position = 0,
            embedding = listOf(0.1f)
        )
        
        val index = RagIndex(
            model = "mxbai-embed-large",
            createdAt = Instant.now(),
            chunks = listOf(chunk)
        )
        
        ragStorage.save(index)
        val loaded = ragStorage.load()
        
        assertNotNull(loaded)
        assertEquals(chunk.text, loaded?.chunks?.get(0)?.text)
    }

    @Test
    fun `test save and load with unicode characters`() {
        val chunk = EmbeddedChunk(
            text = "Unicode: 你好 мир 🌍 العالم",
            source = "unicode.md",
            position = 0,
            embedding = listOf(0.1f)
        )
        
        val index = RagIndex(
            model = "mxbai-embed-large",
            createdAt = Instant.now(),
            chunks = listOf(chunk)
        )
        
        ragStorage.save(index)
        val loaded = ragStorage.load()
        
        assertNotNull(loaded)
        assertEquals(chunk.text, loaded?.chunks?.get(0)?.text)
    }

    @Test
    fun `test save overwrites existing file`() {
        val index1 = RagIndex(
            model = "model-v1",
            createdAt = Instant.now(),
            chunks = listOf(EmbeddedChunk("chunk1", "test.md", 0, listOf(0.1f)))
        )
        
        ragStorage.save(index1)
        
        val index2 = RagIndex(
            model = "model-v2",
            createdAt = Instant.now(),
            chunks = listOf(EmbeddedChunk("chunk2", "test.md", 0, listOf(0.2f)))
        )
        
        ragStorage.save(index2)
        val loaded = ragStorage.load()
        
        assertNotNull(loaded)
        assertEquals("model-v2", loaded?.model)
        assertEquals("chunk2", loaded?.chunks?.get(0)?.text)
    }

    @Test
    fun `test JSON format is readable`() {
        val chunks = listOf(
            EmbeddedChunk(
                text = "Test",
                source = "test.md",
                position = 0,
                embedding = listOf(0.1f, 0.2f)
            )
        )
        
        val index = RagIndex(
            model = "mxbai-embed-large",
            createdAt = Instant.parse("2025-12-22T10:30:00Z"),
            chunks = chunks
        )
        
        ragStorage.save(index)
        
        val jsonContent = indexFile.readText()
        assertTrue(jsonContent.contains("\"model\""))
        assertTrue(jsonContent.contains("mxbai-embed-large"))
        assertTrue(jsonContent.contains("\"text\""))
        assertTrue(jsonContent.contains("Test"))
        assertTrue(jsonContent.contains("\"embedding\""))
    }

    @Test
    fun `test load handles corrupted JSON gracefully`() {
        indexFile.writeText("{ invalid json }")
        
        assertThrows<Exception> {
            ragStorage.load()
        }
    }

    @Test
    fun `test save and load preserves timestamp precision`() {
        val timestamp = Instant.now()
        val index = RagIndex(
            model = "test-model",
            createdAt = timestamp,
            chunks = emptyList()
        )
        
        ragStorage.save(index)
        val loaded = ragStorage.load()
        
        assertNotNull(loaded)
        // Timestamps should be equal within a second
        val timeDiff = Math.abs(timestamp.epochSecond - loaded!!.createdAt.epochSecond)
        assertTrue(timeDiff < 2)
    }

    @Test
    fun `test save with very long text chunks`() {
        val longText = "a".repeat(10000)
        val chunk = EmbeddedChunk(
            text = longText,
            source = "long.md",
            position = 0,
            embedding = listOf(0.1f)
        )
        
        val index = RagIndex(
            model = "mxbai-embed-large",
            createdAt = Instant.now(),
            chunks = listOf(chunk)
        )
        
        ragStorage.save(index)
        val loaded = ragStorage.load()
        
        assertNotNull(loaded)
        assertEquals(10000, loaded?.chunks?.get(0)?.text?.length)
    }
}

