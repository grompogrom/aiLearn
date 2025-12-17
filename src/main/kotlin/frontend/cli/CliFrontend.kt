package frontend.cli

import core.config.AppConfig
import core.conversation.ConversationManager
import core.conversation.TokenCostCalculator
import core.domain.ChatResponse
import core.mcp.McpError
import core.mcp.McpResult
import core.mcp.McpService
import frontend.Frontend
import frontend.UserInput
import frontend.UserOutput

/**
 * Command-line interface frontend implementation.
 */
class CliFrontend(
    private val config: AppConfig,
    private val mcpService: McpService? = null
) : Frontend {

    private val exitCommands = setOf("exit", "quit")
    private val clearHistoryCommands = setOf("/clear", "/clearhistory", "clear", "clearhistory")
    private val mcpCommands = setOf("/mcp")
    private val tokenCalculator = TokenCostCalculator(config)

    override suspend fun start(conversationManager: ConversationManager) {
        printWelcomeMessage()

        var dialogActive = true

        while (dialogActive) {
            val userInput = readUserInput() ?: continue

            when {
                userInput.isExit -> {
                    println("Завершение работы...")
                    break
                }
                userInput.content.lowercase() in clearHistoryCommands -> {
                    handleClearHistory(conversationManager)
                }
                userInput.content.lowercase() in mcpCommands -> {
                    handleMcpCommand()
                }
                else -> {
                    val shouldContinue = handleUserRequest(conversationManager, userInput.content)
                    if (!shouldContinue) {
                        dialogActive = false
                        println("\n=== Диалог завершен ===")
                    }
                }
            }
        }

        println("Программа завершена.")
    }
    
    /**
     * Creates a summarization callback that notifies the user when summarization is in progress.
     */
    fun createSummarizationCallback(): (Boolean) -> Unit {
        return { isStarting ->
            if (isStarting) {
                println("\n[Summarization] Dialog history exceeds token threshold. Summarizing conversation...")
            } else {
                println("[Summarization] Summary complete. Continuing with summarized context.\n")
            }
        }
    }
    
    /**
     * Creates a reminder check callback that prints reminder check results.
     */
    fun createReminderCheckCallback(): (String) -> Unit {
        return { content ->
            printReminderCheck(content)
        }
    }
    
    /**
     * Formats and prints reminder check results.
     * Output is clearly distinguished from regular conversation with a prefix.
     */
    fun printReminderCheck(content: String) {
        println("\n[Reminder Check] ${content}\n")
    }

    private fun printWelcomeMessage() {
        println("\nВведите 'exit' или 'quit' для выхода в любой момент")
        println("Введите '/clear' или '/clearhistory' для очистки истории диалога")
        println("temp is ${config.temperature}")
    }

    private fun readUserInput(): UserInput? {
        print("\nВвод: ")
        val input = try {
            readln()
        } catch (e: Exception) {
            println("\nEOF reached. Завершение работы...")
            return UserInput(content = "", isExit = true)
        }.trim()

        return when {
            input.isBlank() -> {
                println("Пустой ввод. Попробуйте снова.")
                null
            }
            input.lowercase() in exitCommands -> {
                UserInput(content = input, isExit = true)
            }
            else -> {
                UserInput(content = input)
            }
        }
    }

    private suspend fun handleClearHistory(conversationManager: ConversationManager) {
        try {
            conversationManager.clearHistory()
            println("\n✓ История диалога успешно очищена.")
        } catch (e: Exception) {
            println("\n✗ Ошибка при очистке истории: ${e.message}")
        }
    }

    private suspend fun handleMcpCommand() {
        val service = mcpService
        if (service == null) {
            println("\nMCP сервер не настроен. Убедитесь, что заданы переменные окружения AILEARN_MCP_SSE_HOST и связанные настройки.")
            return
        }

        println("\nЗапрос списка доступных MCP инструментов...")

        when (val result = service.getAvailableTools()) {
            is McpResult.Success -> {
                val tools = result.value
                if (tools.isEmpty()) {
                    println("MCP сервер не вернул ни одного инструмента.")
                    return
                }

                println("\n=== MCP инструменты ===")
                tools.forEachIndexed { index, tool ->
                    println("${index + 1}. ${tool.name}")
                    tool.description?.takeIf { it.isNotBlank() }?.let {
                        println("   Описание: $it")
                    }
                    tool.inputSchema?.takeIf { it.isNotBlank() }?.let {
                        println("   Входная схема: $it")
                    }
                    println()
                }
                println("=======================")
            }
            is McpResult.Error -> {
                when (val error = result.error) {
                    is McpError.NotConfigured -> {
                        println("\nMCP сервер не настроен: ${error.message}")
                    }
                    is McpError.ConnectionFailed -> {
                        println("\nНе удалось подключиться к MCP серверу: ${error.message}")
                    }
                    is McpError.Timeout -> {
                        println("\nТаймаут при обращении к MCP серверу: ${error.message}")
                    }
                    is McpError.ServerError -> {
                        println("\nMCP сервер вернул ошибку: ${error.message}")
                    }
                    is McpError.InvalidResponse -> {
                        println("\nНекорректный ответ MCP сервера: ${error.message}")
                    }
                }
                println("Вы можете проверить настройки MCP сервера и попробовать снова.")
            }
        }
    }

    private suspend fun handleUserRequest(
        conversationManager: ConversationManager,
        userInput: String
    ): Boolean {
        return try {
            val response = conversationManager.sendRequest(userInput)
            val output = formatResponse(response)
            
            println(output.content)
            output.tokenUsage?.let { print(it) }

            !output.isDialogEnd
        } catch (e: Exception) {
            println("\nПроизошла ошибка: ${e.message}")
            println("Попробуйте снова или введите 'exit' для выхода.")
            true // Continue dialog on error
        }
    }

    private fun formatResponse(response: ChatResponse): UserOutput {
        val content = response.content
        val tokenUsage = tokenCalculator.formatTokenUsage(response.usage)
        
        val isDialogEnd = content.contains(config.dialogEndMarker)
        val cleanedContent = if (isDialogEnd) {
            content.replace(config.dialogEndMarker, "").trim()
        } else {
            content
        }

        return UserOutput(
            content = cleanedContent,
            tokenUsage = tokenUsage,
            isDialogEnd = isDialogEnd
        )
    }
}
