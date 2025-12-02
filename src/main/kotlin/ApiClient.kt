import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    fun sendRequest(userContent: String): String {
        return runBlocking {
            var lastError: Exception? = null
            var lastContent: String? = null
            
            repeat(Config.MAX_RETRIES) { attempt ->
                try {
                    val content = performRequest(userContent)
                    lastContent = content
                    
                    // Пытаемся распарсить JSON
                    val cleanedContent = content
                        .replace(Regex("```json\\s*"), "")
                        .replace(Regex("```\\s*"), "")
                        .trim()
                    
                    val aiResponse = json.decodeFromString<AiResponse>(cleanedContent)
                    
                    // Выводим информацию о ретраях, если они были
                    if (attempt > 0) {
                        println("(Потребовалось $attempt ${getRetryWord(attempt)} для получения валидного ответа)")
                    }
                    
                    return@runBlocking formatResponse(aiResponse)
                } catch (e: Exception) {
                    lastError = e
                    
                    // Если это последняя попытка, выбрасываем исключение
                    if (attempt == Config.MAX_RETRIES - 1) {
                        val errorMessage = buildString {
                            appendLine("Не удалось получить ответ в формате JSON после ${Config.MAX_RETRIES} попыток.")
                            if (lastContent != null) {
                                appendLine("Последний ответ:")
                                appendLine(lastContent)
                            }
                            appendLine()
                            appendLine("Ошибка парсинга: ${e.message}")
                        }
                        throw RuntimeException(errorMessage)
                    }
                }
            }
            
            // Этот код не должен выполниться, но на всякий случай
            throw RuntimeException("Неожиданная ошибка при выполнении запроса")
        }
    }
    
    private fun getRetryWord(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "ретрай"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "ретрая"
            else -> "ретраев"
        }
    }
    
    private suspend fun performRequest(userContent: String): String {
        val request = ChatRequest(
            model = Config.MODEL,
            messages = listOf(
                Message(
                    role = "system",
                    content = Config.SYSTEM_PROMPT,
                ),
                Message(
                    role = "user",
                    content = userContent,
                )
            ),
            max_tokens = Config.MAX_TOKENS
        )
        
        val response = client.post(Config.API_URL) {
            header("accept", "application/json")
            header("content-type", "application/json")
            header("Authorization", "Bearer ${Config.API_KEY}")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        
        val responseBody = response.bodyAsText()
        val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
        
        // Извлекаем content из ответа
        return chatResponse.choices.firstOrNull()?.message?.content
            ?: throw RuntimeException("Ответ не содержит содержимого")
    }

    private fun formatResponse(aiResponse: AiResponse): String {
        return buildString {
            appendLine("Ответ:")
            appendLine(aiResponse.answer)
            appendLine()
            appendLine("Рекомендации:")
            appendLine(aiResponse.recomendation)
        }
    }

    fun close() {
        client.close()
    }
}

