package core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for Message domain model and factory method.
 */
class MessageTest {

    @Test
    fun `create factory method creates message with correct role`() {
        val message = Message.create(MessageRole.USER, "Test content")
        
        assertEquals(MessageRole.USER, message.role)
    }

    @Test
    fun `create factory method creates message with correct content`() {
        val content = "Test message content"
        val message = Message.create(MessageRole.ASSISTANT, content)
        
        assertEquals(content, message.content)
    }

    @Test
    fun `create factory method defaults disableSearch to true`() {
        val message = Message.create(MessageRole.USER, "Content")
        
        assertTrue(message.disableSearch)
    }

    @Test
    fun `create factory method respects disableSearch parameter`() {
        val message1 = Message.create(MessageRole.USER, "Content", disableSearch = false)
        val message2 = Message.create(MessageRole.USER, "Content", disableSearch = true)
        
        assertFalse(message1.disableSearch)
        assertTrue(message2.disableSearch)
    }

    @Test
    fun `create factory method works with all message roles`() {
        val systemMessage = Message.create(MessageRole.SYSTEM, "System prompt")
        val userMessage = Message.create(MessageRole.USER, "User question")
        val assistantMessage = Message.create(MessageRole.ASSISTANT, "Assistant response")
        
        assertEquals(MessageRole.SYSTEM, systemMessage.role)
        assertEquals(MessageRole.USER, userMessage.role)
        assertEquals(MessageRole.ASSISTANT, assistantMessage.role)
    }

    @Test
    fun `create factory method handles empty content`() {
        val message = Message.create(MessageRole.USER, "")
        
        assertEquals("", message.content)
        assertEquals(MessageRole.USER, message.role)
    }

    @Test
    fun `create factory method handles long content`() {
        val longContent = "A".repeat(10000)
        val message = Message.create(MessageRole.ASSISTANT, longContent)
        
        assertEquals(longContent, message.content)
    }

    @Test
    fun `create factory method handles special characters in content`() {
        val specialContent = "Content with \"quotes\", 'apostrophes', and\nnewlines\tand\ttabs"
        val message = Message.create(MessageRole.USER, specialContent)
        
        assertEquals(specialContent, message.content)
    }

    @Test
    fun `message data class equality works correctly`() {
        val message1 = Message.create(MessageRole.USER, "Content", disableSearch = true)
        val message2 = Message.create(MessageRole.USER, "Content", disableSearch = true)
        val message3 = Message.create(MessageRole.USER, "Different", disableSearch = true)
        val message4 = Message.create(MessageRole.USER, "Content", disableSearch = false)
        
        assertEquals(message1, message2)
        assertTrue(message1 != message3)
        assertTrue(message1 != message4)
    }

    @Test
    fun `message data class toString includes all fields`() {
        val message = Message.create(MessageRole.ASSISTANT, "Test", disableSearch = false)
        val toString = message.toString()
        
        assertTrue(toString.contains("ASSISTANT") || toString.contains("assistant"))
        assertTrue(toString.contains("Test"))
    }
}

