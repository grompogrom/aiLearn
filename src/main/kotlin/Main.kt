import kotlinx.coroutines.runBlocking

private val EXIT_COMMANDS = setOf("exit", "quit")

fun main() = runBlocking {
    ApiClient().use { apiClient ->
        printWelcomeMessage()
        
        var dialogActive = true
        
        while (dialogActive) {
            val userInput = readUserInput() ?: continue
            
            when {
                userInput.isExitCommand() -> {
                    println("Завершение работы...")
                    break
                }
                else -> {
                    val shouldContinue = handleUserRequest(apiClient, userInput)
                    if (!shouldContinue) {
                        dialogActive = false
                        println("\n=== Диалог завершен ===")
                    }
                }
            }
        }
    }
    
    println("Программа завершена.")
}

private fun printWelcomeMessage() {
    println("\nВведите 'exit' или 'quit' для выхода в любой момент")
    println("temp is ${Config.TEMPERATURE}")
}

private fun readUserInput(): String? {
    print("\nВвод: ")
    val input = readln().trim()
    
    return when {
        input.isBlank() -> {
            println("Пустой ввод. Попробуйте снова.")
            null
        }
        else -> input
    }
}

private fun String.isExitCommand(): Boolean {
    return this.lowercase() in EXIT_COMMANDS
}

private suspend fun handleUserRequest(apiClient: ApiClient, userInput: String): Boolean {
    return try {
        val apiResponse = apiClient.sendRequest(userInput)
        val content = apiResponse.content
        
        // Печатаем информацию о токенах
        printTokenUsage(apiResponse.usage)
        
        // Проверяем, содержит ли ответ маркер завершения диалога
        if (content.contains(Config.DIALOG_END_MARKER)) {
            // Выводим ответ без маркера
            val cleanedContent = content.replace(Config.DIALOG_END_MARKER, "").trim()
            println(cleanedContent)
            false // Диалог завершен
        } else {
            println(content)
            true // Продолжаем диалог
        }
    } catch (e: Exception) {
        println("\nПроизошла ошибка: ${e.message}")
        println("Попробуйте снова или введите 'exit' для выхода.")
        true // Продолжаем диалог при ошибке
    }
}

private fun printTokenUsage(usage: Usage?) {
    if (usage != null) {
        println("\n--- Token Usage ---")
        usage.prompt_tokens?.let { println("Prompt tokens: $it") }
        usage.completion_tokens?.let { println("Completion tokens: $it") }
        usage.total_tokens?.let { 
            println("Total tokens: $it")
            val price = calculatePrice(it)
            println("Price: $${String.format("%.6f", price)}")
        }
        println("------------------\n")
    }
}

private fun calculatePrice(totalTokens: Int): Double {
    return (totalTokens / 1_000_000.0) * Config.PRICE_MILLION_TOKENS
}

