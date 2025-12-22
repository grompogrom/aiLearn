package frontend.cli

import core.config.AppConfig
import core.conversation.ConversationManager
import core.conversation.TokenCostCalculator
import core.domain.ChatResponse
import core.mcp.McpError
import core.mcp.McpResult
import core.mcp.McpService
import core.rag.IndexingService
import core.reminder.ReminderChecker
import frontend.Frontend
import frontend.UserInput
import frontend.UserOutput
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(CliFrontend::class.java)

/**
 * Command-line interface frontend implementation.
 */
class CliFrontend(
    private val config: AppConfig,
    private val mcpService: McpService? = null,
    private val reminderChecker: ReminderChecker? = null,
    private val indexingService: IndexingService? = null
) : Frontend {

    private val exitCommands = setOf("exit", "quit")
    private val clearHistoryCommands = setOf("/clear", "/clearhistory", "clear", "clearhistory")
    private val mcpCommands = setOf("/mcp")
    private val reminderCommands = setOf("/reminder")
    private val indexCommands = setOf("/index")
    private val tokenCalculator = TokenCostCalculator(config)

    override suspend fun start(conversationManager: ConversationManager) {
        logger.info("Starting CLI frontend")
        printWelcomeMessage()

        var dialogActive = true

        while (dialogActive) {
            val userInput = readUserInput() ?: continue

            when {
                userInput.isExit -> {
                    logger.info("User requested exit")
                    println("Завершение работы...")
                    break
                }
                userInput.content.lowercase() in clearHistoryCommands -> {
                    logger.debug("User requested history clear")
                    handleClearHistory(conversationManager)
                }
                userInput.content.lowercase() in mcpCommands -> {
                    logger.debug("User requested MCP command")
                    handleMcpCommand()
                }
                userInput.content.lowercase() in reminderCommands -> {
                    logger.debug("User requested reminder command")
                    handleReminderCommand()
                }
                userInput.content.lowercase() in indexCommands -> {
                    logger.debug("User requested index command")
                    handleIndexCommand()
                }
                else -> {
                    logger.debug("Processing user request (length: ${userInput.content.length})")
                    val shouldContinue = handleUserRequest(conversationManager, userInput.content)
                    if (!shouldContinue) {
                        logger.info("Dialog ended by LLM response")
                        dialogActive = false
                        println("\n=== Диалог завершен ===")
                    }
                }
            }
        }

        logger.info("CLI frontend stopped")
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
        println("\n[Reminder Check] \n${content}\n")
    }

    private fun printWelcomeMessage() {
        println("\nВведите 'exit' или 'quit' для выхода в любой момент")
        println("Введите '/clear' или '/clearhistory' для очистки истории диалога")
        println("Введите '/reminder' для включения/выключения проверки напоминаний (по умолчанию выключена)")
        println("Введите '/index' для создания RAG индекса из документов")
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
            logger.info("Clearing conversation history")
            conversationManager.clearHistory()
            logger.info("Conversation history cleared successfully")
            println("\n✓ История диалога успешно очищена.")
        } catch (e: Exception) {
            logger.error("Error clearing conversation history", e)
            println("\n✗ Ошибка при очистке истории: ${e.message}")
        }
    }

    private suspend fun handleMcpCommand() {
        logger.debug("Handling MCP command")
        val service = mcpService
        if (service == null) {
            logger.warn("MCP service not available")
            println("\nMCP сервер не настроен. Убедитесь, что заданы переменные окружения AILEARN_MCP_SSE_HOST и связанные настройки.")
            return
        }

        println("\nЗапрос списка доступных MCP инструментов...")
        logger.debug("Requesting available MCP tools")

        when (val result = service.getAvailableTools()) {
            is McpResult.Success -> {
                val tools = result.value
                logger.info("Retrieved ${tools.size} MCP tools")
                if (tools.isEmpty()) {
                    logger.warn("MCP server returned no tools")
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
                logger.error("Failed to get MCP tools: ${result.error}")
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
    
    private fun handleReminderCommand() {
        val checker = reminderChecker
        if (checker == null) {
            println("\nПроверка напоминаний недоступна. MCP сервис не настроен.")
            return
        }
        
        val wasRunning = checker.isRunning()
        val nowRunning = checker.toggle()
        
        if (nowRunning && !wasRunning) {
            println("\n✓ Проверка напоминаний включена. Проверка будет выполняться каждую минуту.")
        } else if (!nowRunning && wasRunning) {
            println("\n✓ Проверка напоминаний выключена.")
        } else {
            println("\nПроверка напоминаний уже ${if (nowRunning) "включена" else "выключена"}.")
        }
    }
    
    private suspend fun handleIndexCommand() {
        logger.debug("Handling index command")
        val service = indexingService
        if (service == null) {
            logger.warn("Indexing service not available")
            println("\nСервис индексации недоступен. Ollama не настроена.")
            return
        }
        
        println("\n=== Создание RAG индекса ===")
        
        try {
            val chunksCount = service.buildIndex()
            
            if (chunksCount > 0) {
                println("\n✅ Индекс успешно создан!")
                println("Всего проиндексировано фрагментов: $chunksCount")
                println("Индекс сохранен в: dataForRag/indexed/index.json")
            } else {
                println("\n⚠️ Индекс создан, но не содержит фрагментов.")
            }
        } catch (e: Exception) {
            logger.error("Failed to build index", e)
            println("\n✗ Ошибка при создании индекса: ${e.message}")
            println("Убедитесь, что:")
            println("  - Ollama запущена (http://127.0.0.1:11434)")
            println("  - Модель mxbai-embed-large доступна")
            println("  - Директория dataForRag/raw содержит .md файлы")
        }
        
        println("========================\n")
    }

    private suspend fun handleUserRequest(
        conversationManager: ConversationManager,
        userInput: String
    ): Boolean {
        return try {
            val response = conversationManager.sendRequest(userInput)
            val output = formatResponse(response)
            
            logger.debug("Response formatted (isDialogEnd: ${output.isDialogEnd})")
            println(output.content)
            output.tokenUsage?.let { print(it) }

            !output.isDialogEnd
        } catch (e: Exception) {
            logger.error("Error handling user request", e)
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
