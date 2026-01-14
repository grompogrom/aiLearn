package frontend.cli

import core.config.AppConfig
import core.conversation.ConversationManager
import core.conversation.TokenCostCalculator
import core.domain.ChatResponse
import core.mcp.McpError
import core.mcp.McpResult
import core.mcp.McpService
import core.rag.IndexingService
import core.rag.RagIndexNotFoundException
import core.rag.RagQueryService
import core.reminder.ReminderChecker
import frontend.Frontend
import frontend.UserInput
import frontend.UserOutput
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(CliFrontend::class.java)

/**
 * Command-line interface frontend implementation.
 */
class CliFrontend(
    private val config: AppConfig,
    private val mcpService: McpService? = null,
    private val reminderChecker: ReminderChecker? = null,
    private val indexingService: IndexingService? = null,
    private val ragQueryService: RagQueryService? = null
) : Frontend {

    private val exitCommands = setOf("exit", "quit","/exit", "/quit")
    private val clearHistoryCommands = setOf("/clear", "/clearhistory", "clear", "clearhistory")
    private val mcpCommands = setOf("/mcp")
    private val reminderCommands = setOf("/reminder")
    private val indexCommands = setOf("/index")
    private val askCommands = setOf("/ask", "/rag", "/help")
    private val reviewCommands = setOf("/review")
    private val tokenCalculator = TokenCostCalculator(config)
    
    // RAG mode state - when enabled, all queries use RAG system
    private var ragEnabled: Boolean = false

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
                userInput.content.lowercase() in askCommands -> {
                    logger.debug("User requested RAG toggle")
                    handleRagToggle()
                }
                userInput.content.lowercase().startsWith("/rag ") -> {
                    logger.debug("User requested one-time RAG query")
                    val question = userInput.content.substring(5).trim()
                    handleAskCommand(question)
                }
                userInput.content.lowercase().startsWith("/ask ") -> {
                    logger.debug("User requested one-time RAG query via /ask")
                    val question = userInput.content.substring(5).trim()
                    handleAskCommand(question)
                }
                userInput.content.lowercase().startsWith("/review ") -> {
                    logger.debug("User requested review command")
                    val mrLink = userInput.content.substring(8).trim()
                    handleReviewCommand(conversationManager, mrLink)
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
        println("Введите '/rag' для включения/выключения RAG режима (по умолчанию выключен)")
        println("Введите '/index' для создания RAG индекса из документов")
        println("Введите '/ask <вопрос>' для разового поиска ответа в базе знаний")
        println("Введите '/review <ссылка на MR>' для review merge request через GitHub API")
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
    
    /**
     * Toggles RAG mode on/off for all queries.
     */
    private fun handleRagToggle() {
        logger.debug("Toggling RAG mode")
        val service = ragQueryService
        if (service == null) {
            logger.warn("RAG query service not available")
            println("\nRAG сервис недоступен. Ollama не настроена.")
            return
        }
        
        ragEnabled = !ragEnabled
        
        if (ragEnabled) {
            logger.info("RAG mode enabled")
            println("\n✓ RAG режим включен. Все запросы будут использовать базу знаний.")
        } else {
            logger.info("RAG mode disabled")
            println("\n✓ RAG режим выключен.")
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
            val currentDir = System.getProperty("user.dir")
            val chunksCount = service.buildIndex(currentDir)

            if (chunksCount > 0) {
                println("\n✅ Индекс успешно создан!")
                println("Всего проиндексировано фрагментов: $chunksCount")
                println("Индекс сохранен в: dataForRag/indexed/index.json")
                println("Поиск файлов осуществлялся в директории: $currentDir")
            } else {
                println("\n⚠️ Индекс создан, но не содержит фрагментов.")
            }
        } catch (e: Exception) {
            logger.error("Failed to build index", e)
            println("\n✗ Ошибка при создании индекса: ${e.message}")
            println("Убедитесь, что:")
            println("  - Ollama запущена (http://127.0.0.1:11434)")
            println("  - Модель mxbai-embed-large доступна")
            println("  - Текущая директория содержит .md файлы")
        }
        
        println("========================\n")
    }
    
    private suspend fun handleAskCommand(question: String) {
        logger.debug("Handling ask command with question: $question")
        val service = ragQueryService
        if (service == null) {
            logger.warn("RAG query service not available")
            println("\nRAG сервис недоступен. Ollama не настроена.")
            return
        }
        
        if (question.isBlank()) {
            println("\nУкажите вопрос: /ask <ваш вопрос>")
            return
        }
        
        println("\n🔍 Поиск в базе знаний...")
        
        try {
            val result = service.query(question)
            
            // Display retrieved chunks
            println("\n📚 Найдено релевантных фрагментов: ${result.retrievedChunks.size}")
            result.retrievedChunks.forEachIndexed { index, chunk ->
                println()
                // Check if re-ranking was used (both scores present)
                if (chunk.cosineScore != null && chunk.llmScore != null) {
                    println("  ${index + 1}. [${chunk.source}] Cosine: ${"%.2f".format(chunk.cosineScore)} → LLM: ${"%.2f".format(chunk.llmScore)}")
                } else {
                    println("  ${index + 1}. [${chunk.source}] Релевантность: ${"%.2f".format(chunk.similarity)}")
                }
                println("  ---")
                // Display chunk content with indentation
                chunk.text.lines().forEach { line ->
                    println("  $line")
                }
                println("  ---")
            }
            
            // Display LLM answer
            println("\n🤖 Ответ:\n")
            println(result.answer)
            println()
        } catch (e: RagIndexNotFoundException) {
            logger.warn("RAG index not found", e)
            println("\n✗ ${e.message}")
            println("Используйте команду /index для создания индекса.")
        } catch (e: Exception) {
            logger.error("Failed to execute RAG query", e)
            println("\n✗ Ошибка при выполнении запроса: ${e.message}")
            println("Убедитесь, что Ollama запущена и индекс создан.")
        }
    }
    
    private suspend fun handleUserRequest(
        conversationManager: ConversationManager,
        userInput: String
    ): Boolean {
        return try {
            // If RAG is enabled, route through ConversationManager with RAG
            if (ragEnabled && ragQueryService != null) {
                logger.debug("RAG mode is enabled, routing through ConversationManager with RAG")
                
                try {
                    val response = conversationManager.sendRequestWithRag(userInput, ragQueryService!!)
                    
                    // Display answer
                    println("\n🤖 Ответ:\n")
                    println(response.content)
                    println()
                    
                    // Display retrieved chunks if available
                    response.retrievedChunks?.takeIf { it.isNotEmpty() }?.let { chunks ->
                        println("📚 Использованные фрагменты (${chunks.size}):")
                        chunks.forEachIndexed { index, chunk ->
                            println()
                            // Check if re-ranking was used (both scores present)
                            if (chunk.cosineScore != null && chunk.llmScore != null) {
                                println("  ${index + 1}. [${chunk.source}] Cosine: ${"%.2f".format(chunk.cosineScore)} → LLM: ${"%.2f".format(chunk.llmScore)}")
                            } else {
                                println("  ${index + 1}. [${chunk.source}] Релевантность: ${"%.2f".format(chunk.similarity)}")
                            }
                            println("  ---")
                            // Display chunk content with indentation
                            chunk.text.lines().forEach { line ->
                                println("  $line")
                            }
                            println("  ---")
                        }
                        println()
                    }
                    
                    return true  // Continue dialog
                } catch (e: RagIndexNotFoundException) {
                    logger.warn("RAG index not found", e)
                    println("\n✗ ${e.message}")
                    println("Используйте команду /index для создания индекса.")
                    println("RAG режим остается включенным. Используйте /rag для выключения.")
                    return true
                } catch (e: Exception) {
                    logger.error("RAG query failed", e)
                    println("\n✗ Ошибка RAG запроса: ${e.message}")
                    println("Убедитесь, что Ollama запущена и индекс создан.")
                    return true
                }
            }
            
            // Normal flow without RAG
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
    
    /**
     * Handles the /review command.
     * Parses MR link, gets diff via GitHub MCP, gets project context via RAG, and performs review.
     */
    private suspend fun handleReviewCommand(
        conversationManager: ConversationManager,
        mrLink: String
    ) {
        logger.info("Handling review command for MR: $mrLink")
        
        if (mrLink.isBlank()) {
            println("\n✗ Укажите ссылку на MR: /review <ссылка на MR>")
            println("Пример: /review https://github.com/owner/repo/pull/123")
            return
        }
        
        // Parse MR link to extract owner, repo, and PR number
        val (owner, repo, prNumber) = parseMrLink(mrLink)
            ?: run {
                println("\n✗ Некорректная ссылка на MR. Ожидается формат:")
                println("  https://github.com/owner/repo/pull/123")
                println("  или")
                println("  https://github.com/owner/repo/merge_requests/123")
                return
            }
        
        logger.debug("Parsed MR link: owner=$owner, repo=$repo, prNumber=$prNumber")
        
        // Get diff via GitHub API
        println("\n🔍 Получение диффа MR через GitHub API...")
        val mrDiff = getMrDiffViaGithub(owner, repo, prNumber)
            ?: run {
                println("\n✗ Не удалось получить дифф MR через GitHub API.")
                println("Убедитесь, что:")
                println("  - Установлена переменная окружения AILEARN_GITHUB_TOKEN с GitHub Personal Access Token")
                println("  - Токен имеет доступ к репозиторию (scope repo)")
                println("  - Ссылка на MR указана верно")
                return
            }
        
        logger.info("Retrieved MR diff (length: ${mrDiff.length})")
        
        // Get project context via RAG (optional)
        var ragContext: String? = null
        if (ragQueryService != null) {
            try {
                println("📚 Получение контекста проекта через RAG...")
                val ragResult = ragQueryService.query("What is the architecture and main components of this project?")
                ragContext = buildString {
                    append("Project context from knowledge base:\n\n")
                    ragResult.retrievedChunks.forEachIndexed { index, chunk ->
                        append("Source: ${chunk.source}\n")
                        append("Relevance: ${"%.2f".format(chunk.similarity)}\n")
                        append("Content:\n${chunk.text}\n\n")
                    }
                }
                logger.info("Retrieved RAG context (length: ${ragContext.length})")
            } catch (e: Exception) {
                logger.warn("Failed to get RAG context, continuing without it", e)
                println("⚠️ Не удалось получить контекст через RAG, продолжаем без него")
            }
        } else {
            logger.debug("RAG service not available, skipping project context")
        }
        
        // Perform review
        println("\n🤖 Выполнение AI review...")
        try {
            val reviewResponse = conversationManager.performReview(
                mrDiff = mrDiff,
                ragContext = ragContext
            )
            
            println("\n=== AI CODE REVIEW ===\n")
            println(reviewResponse.content)
            println("\n=== END OF REVIEW ===\n")
            
            // Display token usage
            val tokenUsage = tokenCalculator.formatTokenUsage(reviewResponse.usage)
            println(tokenUsage)
        } catch (e: Exception) {
            logger.error("Failed to perform review", e)
            println("\n✗ Ошибка при выполнении review: ${e.message}")
        }
    }
    
    /**
     * Parses a GitHub MR/PR link to extract owner, repo, and PR number.
     * Supports both GitHub PR format and GitLab MR format.
     * 
     * @param link The MR/PR link
     * @return Triple of (owner, repo, prNumber) or null if parsing fails
     */
    private fun parseMrLink(link: String): Triple<String, String, String>? {
        // GitHub PR format: https://github.com/owner/repo/pull/123
        val githubPattern = Regex("""https?://github\.com/([^/]+)/([^/]+)/(?:pull|merge_requests)/(\d+)""")
        val githubMatch = githubPattern.find(link)
        if (githubMatch != null) {
            val (owner, repo, prNumber) = githubMatch.destructured
            return Triple(owner, repo, prNumber)
        }
        
        // GitLab MR format: https://gitlab.com/owner/repo/-/merge_requests/123
        val gitlabPattern = Regex("""https?://gitlab\.com/([^/]+)/([^/]+)/-/merge_requests/(\d+)""")
        val gitlabMatch = gitlabPattern.find(link)
        if (gitlabMatch != null) {
            val (owner, repo, prNumber) = gitlabMatch.destructured
            return Triple(owner, repo, prNumber)
        }
        
        return null
    }
    
    /**
     * Gets MR diff via GitHub REST API.
     *
     * Uses endpoint:
     *   GET https://api.github.com/repos/{owner}/{repo}/pulls/{prNumber}
     * with header:
     *   Accept: application/vnd.github.v3.diff
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber PR number
     * @return The diff as string, or null if failed
     */
    private suspend fun getMrDiffViaGithub(owner: String, repo: String, prNumber: String): String? {
        val token = config.githubToken
        if (token.isBlank()) {
            logger.warn("GitHub token is not configured")
            return null
        }

        val url = "https://api.github.com/repos/$owner/$repo/pulls/$prNumber"
        logger.info("Requesting PR diff from GitHub API: $url")

        val client = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = config.requestTimeoutMillis
            }
        }

        return try {
            val response: HttpResponse = client.get(url) {
                header(HttpHeaders.Accept, "application/vnd.github.v3.diff")
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.UserAgent, "aiLearn")
            }

            logger.debug("GitHub API response status: ${response.status.value} ${response.status.description}")

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                logger.warn("GitHub API returned error ${response.status.value}: $body")
                null
            } else {
                val diff = response.bodyAsText()
                if (diff.isBlank()) {
                    logger.warn("GitHub API returned empty diff")
                    null
                } else {
                    diff
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to get PR diff from GitHub API", e)
            null
        } finally {
            client.close()
        }
    }
}
