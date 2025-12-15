package api.mcp

import core.mcp.McpResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class McpSseResponseParserTest {

    @Test
    fun `parseToolsEventData returns tools on valid response`() {
        val json = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "result": {
                "tools": [
                  {
                    "name": "search",
                    "description": "Performs a web search",
                    "input_schema": {
                      "type": "object",
                      "properties": {
                        "query": { "type": "string" }
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val result = McpSseResponseParser.parseToolsEventData(json)

        val success = assertIs<McpResult.Success<List<core.mcp.McpToolInfo>>>(result)
        assertEquals(1, success.value.size)
        assertEquals("search", success.value[0].name)
        assertEquals("Performs a web search", success.value[0].description)
    }

    @Test
    fun `parseToolsEventData returns error on server error`() {
        val json = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "error": {
                "code": 123,
                "message": "Something went wrong"
              }
            }
        """.trimIndent()

        val result = McpSseResponseParser.parseToolsEventData(json)

        assertIs<McpResult.Error>(result)
    }
}


