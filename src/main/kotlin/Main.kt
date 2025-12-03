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
    println("=== Генератор технических заданий (ТЗ) ===")
    println("Опишите, что вы хотите создать, и ассистент поможет составить ТЗ")
    println("Ассистент задаст примерно 5 уточняющих вопросов для уточнения деталей")
    println("После формирования ТЗ диалог завершится автоматически")
    println("\nВведите 'exit' или 'quit' для выхода в любой момент")
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
        val content = apiClient.sendRequest(userInput)
        
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

