package core.conversation

import core.config.AppConfig
import core.domain.ChatRequest
import core.domain.ChatResponse
import core.domain.Message
import core.domain.MessageRole
import core.mcp.McpResult
import core.mcp.McpService
import core.mcp.McpToolInfo
import core.provider.LlmProvider

/**
 * Handles the tool calling loop for LLM interactions.
 * 
 * Flow:
 * 1. User sends message
 * 2. LLM processes and may request tools (via custom format)
 * 3. Tools are executed via MCP
 * 4. Tool results are sent back to LLM
 * 5. LLM provides final answer
 */
class ToolCallingHandler(
    private val llmProvider: LlmProvider,
    private val config: AppConfig,
    private val mcpService: McpService?
) {
    
    private val parser = ToolRequestParser
    private val maxIterations = 10 // Prevent infinite loops
    
    /**
     * Processes a user request with tool calling support.
     * 
     * @param userContent The user's message
     * @param messageHistory Current message history (will be modified)
     * @param temperature Optional temperature override
     * @return Final chat response
     */
    suspend fun processWithTools(
        userContent: String,
        messageHistory: MutableList<Message>,
        temperature: Double? = null
    ): ChatResponse {
            // #region agent log
            try {
                java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
                    """{"sessionId":"debug-session","runId":"run1","hypothesisId":"A,B,D","location":"ToolCallingHandler.kt:40","message":"processWithTools entry","data":{"userContentLength":${userContent.length},"historySize":${messageHistory.size}},"timestamp":${System.currentTimeMillis()}}\n"""
                )
            } catch (e: Exception) {
                // Logging failed, but continue execution
            }
            // #endregion
        
        // Get available tools and create tool description for LLM
        val toolsDescription = getToolsDescription()
        
        // #region agent log
        java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
            """{"sessionId":"debug-session","runId":"run1","hypothesisId":"A","location":"ToolCallingHandler.kt:48","message":"Got tools description","data":{"toolsCount":${toolsDescription.size}},"timestamp":${System.currentTimeMillis()}}\n"""
        )
        // #endregion
        
        // Add system prompt with tool information if tools are available
        val systemPrompt = if (toolsDescription.isNotEmpty()) {
            buildSystemPromptWithTools(config.systemPrompt, toolsDescription)
        } else {
            config.systemPrompt
        }
        
        // Initialize history if needed
        if (messageHistory.isEmpty() && config.useMessageHistory) {
            messageHistory.add(Message.create(MessageRole.SYSTEM, systemPrompt))
        } else if (messageHistory.isNotEmpty() && messageHistory.first().role == MessageRole.SYSTEM) {
            // Update system prompt with tools
            messageHistory[0] = Message.create(MessageRole.SYSTEM, systemPrompt)
        }
        
        // Add user message
        messageHistory.add(Message.create(MessageRole.USER, userContent))
        
        var iteration = 0
        var lastResponse: ChatResponse? = null
        
        while (iteration < maxIterations) {
            iteration++
            
            // #region agent log
            java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
                """{"sessionId":"debug-session","runId":"run1","hypothesisId":"B,D","location":"ToolCallingHandler.kt:72","message":"Loop iteration start","data":{"iteration":$iteration,"historySize":${messageHistory.size}},"timestamp":${System.currentTimeMillis()}}\n"""
            )
            // #endregion
            
            // Create and send request
            val request = ChatRequest(
                model = config.model,
                messages = messageHistory.toList(),
                maxTokens = config.maxTokens,
                temperature = temperature ?: config.temperature
            )
            
            val response = llmProvider.sendRequest(request)
            lastResponse = response
            
            // #region agent log
            java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
                """{"sessionId":"debug-session","runId":"run1","hypothesisId":"A,B,D","location":"ToolCallingHandler.kt:85","message":"LLM response received","data":{"responseLength":${response.content.length},"responsePreview":${response.content.take(200).replace("\"", "\\\"")}},"timestamp":${System.currentTimeMillis()}}\n"""
            )
            // #endregion
            
            // Check for tool requests
            val toolRequests = parser.parseToolRequests(response.content)
            
            // #region agent log
            java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
                """{"sessionId":"debug-session","runId":"run1","hypothesisId":"A","location":"ToolCallingHandler.kt:87","message":"Parsed tool requests","data":{"toolRequestsCount":${toolRequests.size},"toolNames":${toolRequests.map { it.toolName }}},"timestamp":${System.currentTimeMillis()}}\n"""
            )
            // #endregion
            
            if (toolRequests.isEmpty()) {
                // No tool requests - this is the final answer
                // #region agent log
                java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
                    """{"sessionId":"debug-session","runId":"run1","hypothesisId":"B","location":"ToolCallingHandler.kt:90","message":"No tool requests - returning final answer","data":{},"timestamp":${System.currentTimeMillis()}}\n"""
                )
                // #endregion
                messageHistory.add(Message.create(MessageRole.ASSISTANT, response.content))
                return response
            }
            
            // #region agent log
            java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
                """{"sessionId":"debug-session","runId":"run1","hypothesisId":"D","location":"ToolCallingHandler.kt:95","message":"Tool requests found - executing tools","data":{"toolCount":${toolRequests.size}},"timestamp":${System.currentTimeMillis()}}\n"""
            )
            // #endregion
            
            // Add assistant message with tool requests
            messageHistory.add(Message.create(MessageRole.ASSISTANT, response.content))
            
            // Execute tools and collect results
            val toolResults = executeTools(toolRequests)
            
            // #region agent log
            java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
                """{"sessionId":"debug-session","runId":"run1","hypothesisId":"D","location":"ToolCallingHandler.kt:101","message":"Tools executed","data":{"resultsCount":${toolResults.size},"successCount":${toolResults.count { it.second is McpResult.Success }}},"timestamp":${System.currentTimeMillis()}}\n"""
            )
            // #endregion
            
            // Format tool results for LLM
            val toolResultsText = formatToolResults(toolRequests, toolResults)
            
            // #region agent log
            java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
                """{"sessionId":"debug-session","runId":"run1","hypothesisId":"D","location":"ToolCallingHandler.kt:105","message":"Adding tool results to history","data":{"resultsTextLength":${toolResultsText.length}},"timestamp":${System.currentTimeMillis()}}\n"""
            )
            // #endregion
            
            // Add tool results as a user message (LLM will process them)
            messageHistory.add(Message.create(
                MessageRole.USER,
                "Tool execution results:\n$toolResultsText\n\nPlease provide your final answer based on these results."
            ))
            
            // #region agent log
            java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
                """{"sessionId":"debug-session","runId":"run1","hypothesisId":"D","location":"ToolCallingHandler.kt:112","message":"Loop continuing to next iteration","data":{"newHistorySize":${messageHistory.size}},"timestamp":${System.currentTimeMillis()}}\n"""
            )
            // #endregion
        }
        
        // #region agent log
        java.io.File("/Users/vladimir.gromov/Code/AILEARN/aiLearn/.cursor/debug.log").appendText(
            """{"sessionId":"debug-session","runId":"run1","hypothesisId":"B","location":"ToolCallingHandler.kt:115","message":"Max iterations reached","data":{},"timestamp":${System.currentTimeMillis()}}\n"""
        )
        // #endregion
        
        // Max iterations reached
        return lastResponse ?: ChatResponse("Error: Maximum tool calling iterations reached", null)
    }
    
    /**
     * Gets description of available tools for LLM.
     */
    private suspend fun getToolsDescription(): List<McpToolInfo> {
        if (mcpService == null) return emptyList()
        
        return when (val result = mcpService.getAvailableTools()) {
            is McpResult.Success -> result.value
            is McpResult.Error -> emptyList()
        }
    }
    
    /**
     * Builds system prompt with tool information.
     */
    private fun buildSystemPromptWithTools(
        basePrompt: String,
        tools: List<McpToolInfo>
    ): String {
        val toolsDescription = buildString {
            appendLine("\n\nYou have access to the following tools:")
            tools.forEachIndexed { index, tool ->
                appendLine("${index + 1}. ${tool.name}")
                tool.description?.let { appendLine("   Description: $it") }
                tool.inputSchema?.let { appendLine("   Parameters: $it") }
            }
            appendLine("\nTo use a tool, respond with a JSON object in this format:")
            appendLine("""{"tool": "tool_name", "arguments": {"param1": "value1", "param2": "value2"}}""")
            appendLine("\nYou can request multiple tools by using an array:")
            appendLine("""[{"tool": "tool1", "arguments": {...}}, {"tool": "tool2", "arguments": {...}}]""")
            appendLine("\nAfter tool execution, you will receive the results and should provide your final answer.")
        }
        
        return basePrompt + toolsDescription
    }
    
    /**
     * Executes tool requests and returns results.
     */
    private suspend fun executeTools(
        toolRequests: List<ToolRequest>
    ): List<Pair<ToolRequest, McpResult<String>>> {
        if (mcpService == null) {
            return toolRequests.map { it to McpResult.Error(
                core.mcp.McpError.NotConfigured("MCP service not available")
            ) }
        }
        
        return toolRequests.map { request ->
            request to mcpService.callTool(request.toolName, request.arguments)
        }
    }
    
    /**
     * Formats tool results for LLM consumption.
     */
    private fun formatToolResults(
        requests: List<ToolRequest>,
        results: List<Pair<ToolRequest, McpResult<String>>>
    ): String {
        return buildString {
            results.forEachIndexed { index, (request, result) ->
                appendLine("Tool: ${request.toolName}")
                appendLine("Arguments: ${request.arguments}")
                when (result) {
                    is McpResult.Success -> {
                        appendLine("Result: ${result.value}")
                    }
                    is McpResult.Error -> {
                        appendLine("Error: ${result.error}")
                    }
                }
                if (index < results.size - 1) {
                    appendLine("---")
                }
            }
        }
    }
}

