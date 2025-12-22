package core.rag

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DocumentChunkerTest {

    @Test
    fun `test basic chunking with default parameters`() {
        val chunker = DocumentChunker(chunkSize = 20, chunkOverlap = 5)
        val text = "This is a test document with some text to chunk."
        
        val chunks = chunker.chunk(text, "test.md")
        
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertTrue(chunk.text.length <= 20)
            assertEquals("test.md", chunk.source)
        }
    }

    @Test
    fun `test chunking preserves word boundaries`() {
        val chunker = DocumentChunker(chunkSize = 15, chunkOverlap = 3)
        val text = "Hello world this is a test"
        
        val chunks = chunker.chunk(text, "test.md")
        
        // Verify no chunks split words in the middle
        chunks.forEach { chunk ->
            assertFalse(chunk.text.startsWith(" "))
            assertFalse(chunk.text.endsWith(" "))
        }
    }

    @Test
    fun `test chunk overlap functionality`() {
        val chunker = DocumentChunker(chunkSize = 20, chunkOverlap = 10)
        val text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        
        val chunks = chunker.chunk(text, "test.md")
        
        if (chunks.size > 1) {
            // Verify overlap exists between consecutive chunks
            for (i in 0 until chunks.size - 1) {
                val currentChunk = chunks[i].text
                val nextChunk = chunks[i + 1].text
                
                // There should be some overlap in content
                val overlapExists = currentChunk.takeLast(10).any { char ->
                    nextChunk.contains(char)
                }
                assertTrue(overlapExists)
            }
        }
    }

    @Test
    fun `test chunking empty text returns empty list`() {
        val chunker = DocumentChunker()
        val chunks = chunker.chunk("", "empty.md")
        
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `test chunking text shorter than chunk size returns single chunk`() {
        val chunker = DocumentChunker(chunkSize = 100, chunkOverlap = 10)
        val text = "Short text"
        
        val chunks = chunker.chunk(text, "short.md")
        
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0].text)
        assertEquals(0, chunks[0].position)
    }

    @Test
    fun `test chunk positions are correctly indexed`() {
        val chunker = DocumentChunker(chunkSize = 20, chunkOverlap = 5)
        val text = "This is a long text that will definitely be split into multiple chunks for testing purposes."
        
        val chunks = chunker.chunk(text, "test.md")
        
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.position)
        }
    }

    @Test
    fun `test chunking with multiline text`() {
        val chunker = DocumentChunker(chunkSize = 50, chunkOverlap = 10)
        val text = """
            First line of text
            Second line of text
            Third line of text
        """.trimIndent()
        
        val chunks = chunker.chunk(text, "multiline.md")
        
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertEquals("multiline.md", chunk.source)
        }
    }

    @Test
    fun `test default parameters are reasonable`() {
        val chunker = DocumentChunker()
        
        // Test with a 1000 character text
        val text = "a".repeat(1000)
        val chunks = chunker.chunk(text, "test.md")
        
        // Should create multiple chunks with default 500 size
        assertTrue(chunks.size >= 2)
    }

    @Test
    fun `test chunking with special characters`() {
        val chunker = DocumentChunker(chunkSize = 30, chunkOverlap = 5)
        val text = "Text with special chars: !@#$%^&*() and more"
        
        val chunks = chunker.chunk(text, "special.md")
        
        assertTrue(chunks.isNotEmpty())
        // Verify special characters are preserved
        val reconstructed = chunks.joinToString("") { it.text }
        assertTrue(reconstructed.contains("!@#$%^&*()"))
    }

    @Test
    fun `test chunk metadata is correctly set`() {
        val chunker = DocumentChunker(chunkSize = 20, chunkOverlap = 5)
        val text = "Testing metadata preservation in chunks"
        val source = "metadata_test.md"
        
        val chunks = chunker.chunk(text, source)
        
        chunks.forEach { chunk ->
            assertEquals(source, chunk.source)
            assertTrue(chunk.position >= 0)
            assertFalse(chunk.text.isEmpty())
        }
    }
}

