import kotlinx.coroutines.runBlocking

private val EXIT_COMMANDS = setOf("exit", "quit")

fun main() = runBlocking {
    ApiClient().use { apiClient ->
        printWelcomeMessage()
        
        while (true) {
            val userInput = readUserInput() ?: continue
            
            when {
                userInput.isExitCommand() -> {
                    println("Завершение работы...")
                    break
                }
                else -> handleUserRequest(apiClient, userInput)
            }
        }
    }
    
    println("Программа завершена.")
}

private fun printWelcomeMessage() {
    println("=== Терминальная программа для работы с AI ===")
    println("AI URL: ${Config.API_URL}")
    println("Введите 'exit' или 'quit' для выхода")
    println("\nТеперь вводите данные для отправки в API:")
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

private suspend fun handleUserRequest(apiClient: ApiClient, userInput: String) {
    try {
        val content = apiClient.sendRequest(userInput)
        println(content)
    } catch (e: ApiException) {
        println("\n${e.message}")
        println("Попробуйте снова или введите 'exit' для выхода.")
    } catch (e: Exception) {
        println("\nПроизошла ошибка: ${e.message}")
        println("Попробуйте снова или введите 'exit' для выхода.")
    }
}

