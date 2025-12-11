package frontend.cli

import core.config.AppConfig
import core.conversation.ConversationManager
import core.conversation.TokenCostCalculator
import core.domain.ChatResponse
import frontend.Frontend
import frontend.UserInput
import frontend.UserOutput

/**
 * Command-line interface frontend implementation.
 */
class CliFrontend(
    private val config: AppConfig
) : Frontend {

    private val exitCommands = setOf("exit", "quit")
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

    private fun printWelcomeMessage() {
        println("\nВведите 'exit' или 'quit' для выхода в любой момент")
        println("temp is ${config.temperature}")
    }

    private fun readUserInput(): UserInput? {
        print("\nВвод: ")
        val input = readln().trim()

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
