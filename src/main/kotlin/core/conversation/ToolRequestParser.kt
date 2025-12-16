package core.conversation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents a tool call request parsed from LLM response.
 */
data class ToolRequest(
    val toolName: String,
    val arguments: String // JSON string
)

/**
 * Parser for detecting tool requests in LLM responses.
 * 
 * The parser looks for JSON structures in the LLM response that indicate
 * a tool call request. Multiple formats are supported:
 * 1. JSON object: {"tool": "tool_name", "arguments": {...}}
 * 2. JSON array: [{"tool": "tool_name", "arguments": {...}}]
 * 3. Markdown code block with JSON
 */
object ToolRequestParser {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Parses tool requests from LLM response text.
     * Returns empty list if no tool requests are found.
     */
    fun parseToolRequests(responseText: String): List<ToolRequest> {
        val requests = mutableListOf<ToolRequest>()
        
        // Try to extract JSON from markdown code blocks
        val jsonText = extractJsonFromMarkdown(responseText) ?: responseText
        
        // Try parsing as single object
        parseAsSingleObject(jsonText)?.let { requests.add(it) }
        
        // Try parsing as array
        if (requests.isEmpty()) {
            requests.addAll(parseAsArray(jsonText))
        }
        
        // Try parsing inline JSON patterns
        if (requests.isEmpty()) {
            requests.addAll(parseInlinePatterns(responseText))
        }
        
        return requests
    }
    
    /**
     * Extracts JSON from markdown code blocks.
     */
    private fun extractJsonFromMarkdown(text: String): String? {
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n([\\s\\S]*?)\\n```", RegexOption.IGNORE_CASE)
        return codeBlockRegex.find(text)?.groupValues?.get(1)?.trim()
    }
    
    /**
     * Tries to parse as a single tool request object.
     */
    private fun parseAsSingleObject(text: String): ToolRequest? {
        return try {
            val jsonObj = json.parseToJsonElement(text).jsonObject
            extractToolRequest(jsonObj)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Tries to parse as an array of tool requests.
     */
    private fun parseAsArray(text: String): List<ToolRequest> {
        return try {
            val requests = mutableListOf<ToolRequest>()
            
            // Try parsing as direct JSON array
            try {
                val jsonElement = json.parseToJsonElement(text)
                if (jsonElement is JsonArray) {
                    jsonElement.forEach { element ->
                        element.jsonObject?.let { obj ->
                            extractToolRequest(obj)?.let { requests.add(it) }
                        }
                    }
                    if (requests.isNotEmpty()) {
                        return requests
                    }
                }
            } catch (e: Exception) {
                // Not a direct array
            }
            
            // Try parsing as object with nested arrays/objects
            val jsonObj = json.parseToJsonElement(text).jsonObject
            
            // Check for common patterns
            jsonObj["tools"]?.let { element ->
                when {
                    element is JsonArray -> {
                        element.forEach { item ->
                            item.jsonObject?.let { obj ->
                                extractToolRequest(obj)?.let { requests.add(it) }
                            }
                        }
                    }
                    element is JsonObject -> {
                        extractToolRequest(element)?.let { requests.add(it) }
                    }
                }
            }
            
            jsonObj["tool_calls"]?.let { element ->
                when {
                    element is JsonArray -> {
                        element.forEach { item ->
                            item.jsonObject?.let { obj ->
                                extractToolRequest(obj)?.let { requests.add(it) }
                            }
                        }
                    }
                    element is JsonObject -> {
                        extractToolRequest(element)?.let { requests.add(it) }
                    }
                }
            }
            
            requests
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Extracts tool request from JSON object.
     */
    private fun extractToolRequest(jsonObj: JsonObject): ToolRequest? {
        val toolName = jsonObj["tool"]?.jsonPrimitive?.content
            ?: jsonObj["tool_name"]?.jsonPrimitive?.content
            ?: jsonObj["name"]?.jsonPrimitive?.content
            ?: return null
        
        val arguments = jsonObj["arguments"]?.jsonObject
            ?: jsonObj["args"]?.jsonObject
            ?: jsonObj["params"]?.jsonObject
            ?: JsonObject(emptyMap())
        
        return ToolRequest(
            toolName = toolName,
            arguments = arguments.toString()
        )
    }
    
    /**
     * Parses inline patterns like "CALL_TOOL: tool_name(args)"
     */
    private fun parseInlinePatterns(text: String): List<ToolRequest> {
        val requests = mutableListOf<ToolRequest>()
        
        // Pattern: CALL_TOOL: tool_name({"arg": "value"})
        val pattern = Regex("CALL_TOOL\\s*:\\s*(\\w+)\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE)
        pattern.findAll(text).forEach { match ->
            val toolName = match.groupValues[1]
            val argsText = match.groupValues[2]
            try {
                // Try to parse as JSON
                val args = json.parseToJsonElement(argsText).jsonObject
                requests.add(ToolRequest(toolName, args.toString()))
            } catch (e: Exception) {
                // If not JSON, wrap in quotes
                requests.add(ToolRequest(toolName, """{"input": "$argsText"}"""))
            }
        }
        
        return requests
    }
    
    /**
     * Checks if response contains tool requests.
     */
    fun hasToolRequests(responseText: String): Boolean {
        return parseToolRequests(responseText).isNotEmpty()
    }
}

