fun main() {
    val apiClient = ApiClient()

    println("=== Терминальная программа для работы с AI ===")
    println("AI URL: ${Config.API_URL}")
    println("Введите 'exit' или 'quit' для выхода")
    println("\nТеперь вводите данные для отправки в API:")

    while (true) {
        print("\nВвод: ")
        val userInput = readln().trim()

        when {
            userInput.isBlank() -> {
                println("Пустой ввод. Попробуйте снова.")
                continue
            }

            userInput.equals("exit", ignoreCase = true) || userInput.equals("quit", ignoreCase = true) -> {
                println("Завершение работы...")
                break
            }

            else -> {
                try {
                    val content = apiClient.sendRequest(userInput)

                    println(content)
                } catch (e: Exception) {
                    println("\n${e.message}")
                    println("Попробуйте снова или введите 'exit' для выхода.")
                }
            }
        }
    }

    apiClient.close()
    println("Программа завершена.")
}

