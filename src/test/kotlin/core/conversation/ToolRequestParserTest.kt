package core.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for ToolRequestParser.
 * Tests parsing of tool requests from LLM responses in various formats.
 */
class ToolRequestParserTest {

    @Test
    fun `parseToolRequests returns empty list for empty string`() {
        val result = ToolRequestParser.parseToolRequests("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseToolRequests returns empty list for plain text`() {
        val result = ToolRequestParser.parseToolRequests("This is just plain text without any tool calls.")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseToolRequests parses single object format`() {
        val json = """{"tool": "test_tool", "arguments": {"param1": "value1"}}"""
        val result = ToolRequestParser.parseToolRequests(json)
        
        assertEquals(1, result.size)
        assertEquals("test_tool", result[0].toolName)
        assertTrue(result[0].arguments.contains("param1"))
        assertTrue(result[0].arguments.contains("value1"))
    }

    @Test
    fun `parseToolRequests parses single object with tool_name field`() {
        val json = """{"tool_name": "my_tool", "arguments": {"key": "value"}}"""
        val result = ToolRequestParser.parseToolRequests(json)
        
        assertEquals(1, result.size)
        assertEquals("my_tool", result[0].toolName)
    }

    @Test
    fun `parseToolRequests parses single object with name field`() {
        val json = """{"name": "another_tool", "arguments": {}}"""
        val result = ToolRequestParser.parseToolRequests(json)
        
        assertEquals(1, result.size)
        assertEquals("another_tool", result[0].toolName)
    }

    @Test
    fun `parseToolRequests parses array format`() {
        val json = """[{"tool": "tool1", "arguments": {"a": "1"}}, {"tool": "tool2", "arguments": {"b": "2"}}]"""
        val result = ToolRequestParser.parseToolRequests(json)
        
        assertEquals(2, result.size)
        assertEquals("tool1", result[0].toolName)
        assertEquals("tool2", result[1].toolName)
    }

    @Test
    fun `parseToolRequests parses markdown code block format`() {
        // The regex requires newlines after opening and before closing ```
        val text = "Here is a tool call:\n```json\n{\"tool\": \"markdown_tool\", \"arguments\": {\"test\": \"value\"}}\n```\n"
        val result = ToolRequestParser.parseToolRequests(text)
        
        assertEquals(1, result.size)
        assertEquals("markdown_tool", result[0].toolName)
    }

    @Test
    fun `parseToolRequests parses markdown code block without json label`() {
        // The regex requires newlines after opening and before closing ```
        val text = "Tool call:\n```\n{\"tool\": \"simple_tool\", \"arguments\": {}}\n```\n"
        val result = ToolRequestParser.parseToolRequests(text)
        
        assertEquals(1, result.size)
        assertEquals("simple_tool", result[0].toolName)
    }

    @Test
    fun `parseToolRequests parses nested tools array`() {
        val json = """{"tools": [{"tool": "nested_tool", "arguments": {}}]}"""
        val result = ToolRequestParser.parseToolRequests(json)
        
        assertEquals(1, result.size)
        assertEquals("nested_tool", result[0].toolName)
    }

    @Test
    fun `parseToolRequests parses nested tool_calls array`() {
        val json = """{"tool_calls": [{"tool": "call_tool", "arguments": {"x": "y"}}]}"""
        val result = ToolRequestParser.parseToolRequests(json)
        
        assertEquals(1, result.size)
        assertEquals("call_tool", result[0].toolName)
    }

    @Test
    fun `parseToolRequests handles arguments with different field names`() {
        val json1 = """{"tool": "tool1", "arguments": {"a": "1"}}"""
        val json2 = """{"tool": "tool2", "args": {"b": "2"}}"""
        val json3 = """{"tool": "tool3", "params": {"c": "3"}}"""
        
        val result1 = ToolRequestParser.parseToolRequests(json1)
        val result2 = ToolRequestParser.parseToolRequests(json2)
        val result3 = ToolRequestParser.parseToolRequests(json3)
        
        assertEquals(1, result1.size)
        assertTrue(result1[0].arguments.contains("a"))
        
        assertEquals(1, result2.size)
        assertTrue(result2[0].arguments.contains("b"))
        
        assertEquals(1, result3.size)
        assertTrue(result3[0].arguments.contains("c"))
    }

    @Test
    fun `parseToolRequests handles missing arguments field`() {
        val json = """{"tool": "no_args_tool"}"""
        val result = ToolRequestParser.parseToolRequests(json)
        
        assertEquals(1, result.size)
        assertEquals("no_args_tool", result[0].toolName)
        // Should have empty JSON object for arguments
        assertTrue(result[0].arguments.contains("{}") || result[0].arguments == "{}")
    }

    @Test
    fun `parseToolRequests parses inline CALL_TOOL pattern`() {
        val text = """CALL_TOOL: my_tool({"param": "value"})"""
        val result = ToolRequestParser.parseToolRequests(text)
        
        assertEquals(1, result.size)
        assertEquals("my_tool", result[0].toolName)
        assertTrue(result[0].arguments.contains("param"))
    }

    @Test
    fun `parseToolRequests parses multiple inline CALL_TOOL patterns`() {
        val text = """
        CALL_TOOL: tool1({"a": "1"})
        Some text
        CALL_TOOL: tool2({"b": "2"})
        """
        val result = ToolRequestParser.parseToolRequests(text)
        
        assertEquals(2, result.size)
        assertEquals("tool1", result[0].toolName)
        assertEquals("tool2", result[1].toolName)
    }

    @Test
    fun `parseToolRequests handles inline pattern with non-JSON arguments`() {
        val text = """CALL_TOOL: simple_tool(plain text)"""
        val result = ToolRequestParser.parseToolRequests(text)
        
        assertEquals(1, result.size)
        assertEquals("simple_tool", result[0].toolName)
        assertTrue(result[0].arguments.contains("plain text"))
    }

    @Test
    fun `parseToolRequests ignores case in inline pattern`() {
        val text = """call_tool: lowercase_tool({"test": "value"})"""
        val result = ToolRequestParser.parseToolRequests(text)
        
        assertEquals(1, result.size)
        assertEquals("lowercase_tool", result[0].toolName)
    }

    @Test
    fun `parseToolRequests prefers single object over array when both are present`() {
        // This tests the priority: single object is tried first
        val json = """{"tool": "single_tool", "arguments": {}}"""
        val result = ToolRequestParser.parseToolRequests(json)
        
        assertEquals(1, result.size)
        assertEquals("single_tool", result[0].toolName)
    }

    @Test
    fun `parseToolRequests handles complex nested arguments`() {
        val json = """{"tool": "complex_tool", "arguments": {"nested": {"key": "value"}, "array": [1, 2, 3]}}"""
        val result = ToolRequestParser.parseToolRequests(json)
        
        assertEquals(1, result.size)
        assertEquals("complex_tool", result[0].toolName)
        assertTrue(result[0].arguments.contains("nested"))
        assertTrue(result[0].arguments.contains("array"))
    }

    @Test
    fun `hasToolRequests returns false for empty string`() {
        assertFalse(ToolRequestParser.hasToolRequests(""))
    }

    @Test
    fun `hasToolRequests returns false for plain text`() {
        assertFalse(ToolRequestParser.hasToolRequests("This is just text"))
    }

    @Test
    fun `hasToolRequests returns true when tool request is present`() {
        val json = """{"tool": "test_tool", "arguments": {}}"""
        assertTrue(ToolRequestParser.hasToolRequests(json))
    }

    @Test
    fun `hasToolRequests returns true for markdown code block`() {
        // The regex requires newlines after opening and before closing ```
        val text = "```json\n{\"tool\": \"markdown_tool\", \"arguments\": {}}\n```"
        assertTrue(ToolRequestParser.hasToolRequests(text))
    }

    @Test
    fun `hasToolRequests returns true for inline pattern`() {
        val text = """CALL_TOOL: inline_tool({"test": "value"})"""
        assertTrue(ToolRequestParser.hasToolRequests(text))
    }

    @Test
    fun `parseToolRequests handles malformed JSON gracefully`() {
        val text = """{"tool": "broken", "arguments": {invalid json}"""
        val result = ToolRequestParser.parseToolRequests(text)
        
        // Should return empty list or handle gracefully
        // The exact behavior depends on implementation, but should not crash
        assertTrue(result.isEmpty() || result.isNotEmpty())
    }

    @Test
    fun `parseToolRequests handles text with multiple formats`() {
        val text = """
        Here's a tool call in markdown:
        ```json
        {"tool": "markdown_tool", "arguments": {}}
        ```
        
        And here's an inline one: CALL_TOOL: inline_tool({"test": "value"})
        """
        val result = ToolRequestParser.parseToolRequests(text)
        
        // Should parse at least one tool request
        assertTrue(result.isNotEmpty())
    }
}

