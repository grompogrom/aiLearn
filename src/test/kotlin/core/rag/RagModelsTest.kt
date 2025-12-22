package core.rag

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class RagModelsTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `test Chunk creation and properties`() {
        val chunk = Chunk(
            text = "Test text",
            source = "test.md",
            position = 5
        )
        
        assertEquals("Test text", chunk.text)
        assertEquals("test.md", chunk.source)
        assertEquals(5, chunk.position)
    }

    @Test
    fun `test EmbeddedChunk creation and properties`() {
        val embeddedChunk = EmbeddedChunk(
            text = "Test text",
            source = "test.md",
            position = 3,
            embedding = listOf(0.1f, 0.2f, 0.3f)
        )
        
        assertEquals("Test text", embeddedChunk.text)
        assertEquals("test.md", embeddedChunk.source)
        assertEquals(3, embeddedChunk.position)
        assertEquals(3, embeddedChunk.embedding.size)
        assertEquals(0.2f, embeddedChunk.embedding[1])
    }

    @Test
    fun `test RagIndex creation and properties`() {
        val chunks = listOf(
            EmbeddedChunk("chunk1", "test.md", 0, listOf(0.1f)),
            EmbeddedChunk("chunk2", "test.md", 1, listOf(0.2f))
        )
        
        val timestamp = Instant.now()
        val ragIndex = RagIndex(
            model = "mxbai-embed-large",
            createdAt = timestamp,
            chunks = chunks
        )
        
        assertEquals("mxbai-embed-large", ragIndex.model)
        assertEquals(timestamp, ragIndex.createdAt)
        assertEquals(2, ragIndex.chunks.size)
    }

    @Test
    fun `test Chunk serialization`() {
        val chunk = Chunk(
            text = "Test chunk",
            source = "source.md",
            position = 0
        )
        
        val serialized = json.encodeToString(chunk)
        
        assertTrue(serialized.contains("\"text\""))
        assertTrue(serialized.contains("Test chunk"))
        assertTrue(serialized.contains("\"source\""))
        assertTrue(serialized.contains("source.md"))
        assertTrue(serialized.contains("\"position\""))
    }

    @Test
    fun `test Chunk deserialization`() {
        val jsonString = """
            {
                "text": "Deserialized chunk",
                "source": "file.md",
                "position": 42
            }
        """.trimIndent()
        
        val chunk = json.decodeFromString<Chunk>(jsonString)
        
        assertEquals("Deserialized chunk", chunk.text)
        assertEquals("file.md", chunk.source)
        assertEquals(42, chunk.position)
    }

    @Test
    fun `test EmbeddedChunk serialization`() {
        val embeddedChunk = EmbeddedChunk(
            text = "Test",
            source = "test.md",
            position = 0,
            embedding = listOf(0.1f, 0.2f, 0.3f)
        )
        
        val serialized = json.encodeToString(embeddedChunk)
        
        assertTrue(serialized.contains("\"embedding\""))
        assertTrue(serialized.contains("0.1"))
        assertTrue(serialized.contains("0.2"))
        assertTrue(serialized.contains("0.3"))
    }

    @Test
    fun `test EmbeddedChunk deserialization`() {
        val jsonString = """
            {
                "text": "Embedded test",
                "source": "test.md",
                "position": 1,
                "embedding": [0.5, 0.6, 0.7]
            }
        """.trimIndent()
        
        val chunk = json.decodeFromString<EmbeddedChunk>(jsonString)
        
        assertEquals("Embedded test", chunk.text)
        assertEquals(3, chunk.embedding.size)
        assertEquals(0.5f, chunk.embedding[0])
        assertEquals(0.7f, chunk.embedding[2])
    }

    @Test
    fun `test RagIndex serialization`() {
        val chunks = listOf(
            EmbeddedChunk("chunk1", "test.md", 0, listOf(0.1f))
        )
        
        val ragIndex = RagIndex(
            model = "test-model",
            createdAt = Instant.parse("2025-12-22T10:00:00Z"),
            chunks = chunks
        )
        
        val serialized = json.encodeToString(ragIndex)
        
        assertTrue(serialized.contains("\"model\""))
        assertTrue(serialized.contains("test-model"))
        assertTrue(serialized.contains("\"createdAt\""))
        assertTrue(serialized.contains("\"chunks\""))
    }

    @Test
    fun `test RagIndex deserialization`() {
        val jsonString = """
            {
                "model": "mxbai-embed-large",
                "createdAt": "2025-12-22T10:00:00Z",
                "chunks": [
                    {
                        "text": "chunk1",
                        "source": "file.md",
                        "position": 0,
                        "embedding": [0.1, 0.2]
                    }
                ]
            }
        """.trimIndent()
        
        val ragIndex = json.decodeFromString<RagIndex>(jsonString)
        
        assertEquals("mxbai-embed-large", ragIndex.model)
        assertEquals(1, ragIndex.chunks.size)
        assertEquals("chunk1", ragIndex.chunks[0].text)
    }

    @Test
    fun `test EmbeddedChunk with large embedding vector`() {
        val largeEmbedding = List(1024) { it * 0.001f }
        val chunk = EmbeddedChunk(
            text = "Test",
            source = "test.md",
            position = 0,
            embedding = largeEmbedding
        )
        
        val serialized = json.encodeToString(chunk)
        val deserialized = json.decodeFromString<EmbeddedChunk>(serialized)
        
        assertEquals(1024, deserialized.embedding.size)
        assertEquals(largeEmbedding[0], deserialized.embedding[0])
        assertEquals(largeEmbedding[1023], deserialized.embedding[1023])
    }

    @Test
    fun `test RagIndex with empty chunks list`() {
        val ragIndex = RagIndex(
            model = "test-model",
            createdAt = Instant.now(),
            chunks = emptyList()
        )
        
        val serialized = json.encodeToString(ragIndex)
        val deserialized = json.decodeFromString<RagIndex>(serialized)
        
        assertEquals(0, deserialized.chunks.size)
        assertEquals("test-model", deserialized.model)
    }

    @Test
    fun `test RagIndex with multiple chunks from different sources`() {
        val chunks = listOf(
            EmbeddedChunk("chunk1", "file1.md", 0, listOf(0.1f)),
            EmbeddedChunk("chunk2", "file1.md", 1, listOf(0.2f)),
            EmbeddedChunk("chunk3", "file2.md", 0, listOf(0.3f)),
            EmbeddedChunk("chunk4", "file3.md", 0, listOf(0.4f))
        )
        
        val ragIndex = RagIndex(
            model = "test-model",
            createdAt = Instant.now(),
            chunks = chunks
        )
        
        val serialized = json.encodeToString(ragIndex)
        val deserialized = json.decodeFromString<RagIndex>(serialized)
        
        assertEquals(4, deserialized.chunks.size)
        assertEquals("file1.md", deserialized.chunks[0].source)
        assertEquals("file2.md", deserialized.chunks[2].source)
        assertEquals("file3.md", deserialized.chunks[3].source)
    }

    @Test
    fun `test Chunk with special characters in text`() {
        val specialText = "Special: !@#$%^&*()_+{}[]|\\:\";<>?,./"
        val chunk = Chunk(
            text = specialText,
            source = "test.md",
            position = 0
        )
        
        val serialized = json.encodeToString(chunk)
        val deserialized = json.decodeFromString<Chunk>(serialized)
        
        assertEquals(specialText, deserialized.text)
    }

    @Test
    fun `test Chunk with unicode characters`() {
        val unicodeText = "Unicode: 你好 мир 🌍 العالم"
        val chunk = Chunk(
            text = unicodeText,
            source = "unicode.md",
            position = 0
        )
        
        val serialized = json.encodeToString(chunk)
        val deserialized = json.decodeFromString<Chunk>(serialized)
        
        assertEquals(unicodeText, deserialized.text)
    }

    @Test
    fun `test Chunk with multiline text`() {
        val multilineText = """
            Line 1
            Line 2
            Line 3
        """.trimIndent()
        
        val chunk = Chunk(
            text = multilineText,
            source = "test.md",
            position = 0
        )
        
        val serialized = json.encodeToString(chunk)
        val deserialized = json.decodeFromString<Chunk>(serialized)
        
        assertEquals(multilineText, deserialized.text)
    }

    @Test
    fun `test EmbeddedChunk with negative embedding values`() {
        val embedding = listOf(-0.5f, -0.3f, 0.2f, -0.1f)
        val chunk = EmbeddedChunk(
            text = "Test",
            source = "test.md",
            position = 0,
            embedding = embedding
        )
        
        val serialized = json.encodeToString(chunk)
        val deserialized = json.decodeFromString<EmbeddedChunk>(serialized)
        
        assertEquals(4, deserialized.embedding.size)
        assertEquals(-0.5f, deserialized.embedding[0], 0.001f)
        assertEquals(-0.3f, deserialized.embedding[1], 0.001f)
    }

    @Test
    fun `test RagIndex preserves timestamp precision`() {
        val timestamp = Instant.parse("2025-12-22T14:30:45.123456789Z")
        val ragIndex = RagIndex(
            model = "test-model",
            createdAt = timestamp,
            chunks = emptyList()
        )
        
        val serialized = json.encodeToString(ragIndex)
        val deserialized = json.decodeFromString<RagIndex>(serialized)
        
        // Should preserve at least second-level precision
        assertEquals(timestamp.epochSecond, deserialized.createdAt.epochSecond)
    }

    @Test
    fun `test Chunk with very long text`() {
        val longText = "a".repeat(10000)
        val chunk = Chunk(
            text = longText,
            source = "long.md",
            position = 0
        )
        
        val serialized = json.encodeToString(chunk)
        val deserialized = json.decodeFromString<Chunk>(serialized)
        
        assertEquals(10000, deserialized.text.length)
        assertEquals(longText, deserialized.text)
    }

    @Test
    fun `test RagIndex round-trip serialization`() {
        val original = RagIndex(
            model = "mxbai-embed-large",
            createdAt = Instant.now(),
            chunks = listOf(
                EmbeddedChunk("chunk1", "file1.md", 0, listOf(0.1f, 0.2f)),
                EmbeddedChunk("chunk2", "file2.md", 1, listOf(0.3f, 0.4f))
            )
        )
        
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<RagIndex>(serialized)
        val reSerialized = json.encodeToString(deserialized)
        
        // Should be equivalent after round-trip
        assertEquals(original.model, deserialized.model)
        assertEquals(original.chunks.size, deserialized.chunks.size)
        assertEquals(original.chunks[0].text, deserialized.chunks[0].text)
        assertEquals(original.chunks[1].embedding.size, deserialized.chunks[1].embedding.size)
    }

    @Test
    fun `test Chunk with empty source string`() {
        val chunk = Chunk(
            text = "Test",
            source = "",
            position = 0
        )
        
        val serialized = json.encodeToString(chunk)
        val deserialized = json.decodeFromString<Chunk>(serialized)
        
        assertEquals("", deserialized.source)
    }

    @Test
    fun `test EmbeddedChunk with zero values embedding`() {
        val embedding = List(100) { 0.0f }
        val chunk = EmbeddedChunk(
            text = "Test",
            source = "test.md",
            position = 0,
            embedding = embedding
        )
        
        val serialized = json.encodeToString(chunk)
        val deserialized = json.decodeFromString<EmbeddedChunk>(serialized)
        
        assertEquals(100, deserialized.embedding.size)
        assertTrue(deserialized.embedding.all { it == 0.0f })
    }
}

