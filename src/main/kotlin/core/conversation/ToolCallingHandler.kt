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
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ToolCallingHandler::class.java)

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
        logger.debug("Processing request with tools (userContent length: ${userContent.length}, history size: ${messageHistory.size})")
        
        // Get available tools and create tool description for LLM
        val toolsDescription = getToolsDescription()
        logger.info("Available tools: ${toolsDescription.size}")
        
        // Get the existing system prompt from history if available, otherwise use config
        val existingSystemPrompt = if (messageHistory.isNotEmpty() && messageHistory.first().role == MessageRole.SYSTEM) {
            messageHistory.first().content
        } else {
            config.systemPrompt
        }
        
        // Add system prompt with tool information if tools are available
        // Preserve existing system prompt content (which may include summary context)
        val systemPrompt = if (toolsDescription.isNotEmpty()) {
            buildSystemPromptWithTools(existingSystemPrompt, toolsDescription)
        } else {
            existingSystemPrompt
        }
        
        // Initialize history if needed
        if (messageHistory.isEmpty() && config.useMessageHistory) {
            messageHistory.add(Message.create(MessageRole.SYSTEM, systemPrompt))
        } else if (messageHistory.isNotEmpty() && messageHistory.first().role == MessageRole.SYSTEM) {
            // Update system prompt with tools, preserving existing content
            messageHistory[0] = Message.create(MessageRole.SYSTEM, systemPrompt)
        }
        
        // Add user message
        messageHistory.add(Message.create(MessageRole.USER, userContent))
        
        var iteration = 0
        var lastResponse: ChatResponse? = null
        
        while (iteration < maxIterations) {
            iteration++
            logger.debug("Tool calling iteration $iteration/$maxIterations (history size: ${messageHistory.size})")
            
            // Create and send request
            val request = ChatRequest(
                model = config.model,
                messages = messageHistory.toList(),
                maxTokens = config.maxTokens,
                temperature = temperature ?: config.temperature
            )
            
            logger.trace("Sending request to LLM (messages: ${request.messages.size})")
            val response = llmProvider.sendRequest(request)
            lastResponse = response
            logger.debug("Received LLM response (length: ${response.content.length})")
            
            // Check for tool requests
            val toolRequests = parser.parseToolRequests(response.content)
            logger.debug("Parsed ${toolRequests.size} tool request(s): ${toolRequests.map { it.toolName }}")
            
            if (toolRequests.isEmpty()) {
                // No tool requests - this is the final answer
                logger.info("No tool requests found, returning final answer (iteration $iteration)")
                messageHistory.add(Message.create(MessageRole.ASSISTANT, response.content))
                return response
            }
            
            logger.info("Executing ${toolRequests.size} tool(s) in iteration $iteration")
            
            // Add assistant message with tool requests
            messageHistory.add(Message.create(MessageRole.ASSISTANT, response.content))
            
            // Execute tools and collect results
            val toolResults = executeTools(toolRequests)
            val successCount = toolResults.count { it.second is McpResult.Success }
            logger.info("Tool execution completed: $successCount/${toolResults.size} successful")
            
            // Format tool results for LLM
            val toolResultsText = formatToolResults(toolRequests, toolResults)
            logger.trace("Tool results formatted (length: ${toolResultsText.length})")
            
            // Add tool results as a user message (LLM will process them)
            messageHistory.add(Message.create(
                MessageRole.USER,
                "Tool execution results:\n$toolResultsText\n\nPlease provide your final answer based on these results."
            ))
            
            logger.debug("Continuing to next iteration (new history size: ${messageHistory.size})")
        }
        
        // Max iterations reached
        logger.warn("Maximum tool calling iterations ($maxIterations) reached")
        return lastResponse ?: ChatResponse("Error: Maximum tool calling iterations reached", null)
    }
    
    /**
     * Gets description of available tools for LLM.
     */
    private suspend fun getToolsDescription(): List<McpToolInfo> {
        if (mcpService == null) {
            logger.debug("MCP service not available, no tools")
            return emptyList()
        }
        
        return when (val result = mcpService.getAvailableTools()) {
            is McpResult.Success -> {
                logger.debug("Retrieved ${result.value.size} tools from MCP service")
                result.value
            }
            is McpResult.Error -> {
                logger.warn("Failed to get tools from MCP service: ${result.error}")
                emptyList()
            }
        }
    }
    
    /**
     * Builds system prompt with tool information.
     */
    private fun buildSystemPromptWithTools(
        basePrompt: String,
        tools: List<McpToolInfo>
    ): String {
        val toolsDescription = tools.joinToString("\n") { tool ->
            buildString {
                append("- ${tool.name}")
                tool.description?.let { append(": $it") }
                tool.inputSchema?.let { append("\n  Input Schema: $it") }
            }
        }
        
        return """You are an expert AI agent with access to MCP servers. ALWAYS use available MCP tools BEFORE answering or assuming information.

Available MCP tools (from list_tools response):
$toolsDescription

TOOL USAGE INSTRUCTIONS:
- When you need information or actions that require external resources, always check the above list first
- Tools provide structured access to system resources, files, services, and external data sources
- Use tools to avoid guessing, fabricating, or making assumptions about information

HOW TO USE TOOLS:
1. Examine the task carefully to determine if any of the above tools are needed
2. Identify the appropriate tool based on name, description, and schema
3. Construct valid JSON arguments that conform to the tool's input schema
4. When you need to call a tool, respond with ONLY the tool call in this exact format:
   {"tool": "exact_tool_name", "arguments": {"arg1": "value1", "arg2": "value2"}}
5. Wait for the tool execution results before continuing your response
6. After receiving tool results, analyze them and determine if additional tools are needed or if you can answer the original question
7. If multiple tools are needed, chain them appropriately

TOOL CALL FORMAT:
- Response must contain ONLY the JSON tool call when you want to execute a tool
- Do NOT wrap the JSON in ```json ``` or any other formatting
- Do NOT include explanatory text with the tool call - just the JSON
- After the tool result is returned, you can then provide your analysis or call another tool

EXAMPLE TOOL CHAIN:
- User asks: "Show me recent git commits and send a notification"
- You call: {"tool": "git_log", "arguments": {"count": 5}}
- Receive results, then call: {"tool": "send_notification", "arguments": {"message": "recent commits found"}}
- Then provide final response to user

CRITICAL RULES:
1. If task requires external action/data → IMMEDIATELY call EXACT matching tool with VALID JSON args per schema
2. NEVER fabricate data/results - if tool needed → call it FIRST
3. After tool response: analyze output, call follow-up tools if needed, THEN respond
4. Respond ONLY with tool call JSON when tool required (as shown in format section)
5. If no tools are appropriate for the task, respond normally without tool calls
6. Respect the input schema requirements for each tool - invalid arguments will cause errors

User task:"""
    }
    
    /**
     * Executes tool requests and returns results.
     */
    private suspend fun executeTools(
        toolRequests: List<ToolRequest>
    ): List<Pair<ToolRequest, McpResult<String>>> {
        if (mcpService == null) {
            logger.warn("MCP service not available, cannot execute tools")
            return toolRequests.map { it to McpResult.Error(
                core.mcp.McpError.NotConfigured("MCP service not available")
            ) }
        }
        
        return toolRequests.map { request ->
            logger.debug("Executing tool: ${request.toolName}")
            val result = mcpService.callTool(request.toolName, request.arguments)
            when (result) {
                is McpResult.Success -> logger.debug("Tool '${request.toolName}' executed successfully")
                is McpResult.Error -> logger.warn("Tool '${request.toolName}' execution failed: ${result.error}")
            }
            request to result
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

