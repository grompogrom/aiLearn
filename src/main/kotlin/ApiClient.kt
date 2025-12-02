import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ApiClient : AutoCloseable {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun sendRequest(userContent: String): String {
        var lastContent: String? = null
        
        repeat(Config.MAX_RETRIES) { attempt ->
            try {
                val content = performRequest(userContent)
                lastContent = content
                
                val aiResponse = parseJsonResponse(content)
                
                if (attempt > 0) {
                    println("(Потребовалось $attempt ${attempt.getRetryWord()} для получения валидного ответа)")
                }
                
                return formatResponse(aiResponse)
            } catch (e: Exception) {
                if (attempt == Config.MAX_RETRIES - 1) {
                    throw ApiException.InvalidJsonResponse(
                        attempts = Config.MAX_RETRIES,
                        lastContent = lastContent,
                        cause = e
                    )
                }
            }
        }
        
        throw ApiException.RequestFailed("Неожиданная ошибка при выполнении запроса")
    }
    
    private suspend fun performRequest(userContent: String): String {
        val request = createChatRequest(userContent)
        
        val response = client.post(Config.API_URL) {
            configureHeaders()
            setBody(request)
        }
        
        return extractContentFromResponse(response)
    }
    
    private fun createChatRequest(userContent: String): ChatRequest {
        return ChatRequest(
            model = Config.MODEL,
            messages = listOf(
                Message.create(MessageRole.SYSTEM, Config.SYSTEM_PROMPT),
                Message.create(MessageRole.USER, userContent)
            ),
            max_tokens = Config.MAX_TOKENS
        )
    }
    
    private fun HttpRequestBuilder.configureHeaders() {
        header("accept", "application/json")
        header("content-type", "application/json")
        header("Authorization", "Bearer ${Config.API_KEY}")
        contentType(ContentType.Application.Json)
    }
    
    private suspend fun extractContentFromResponse(response: HttpResponse): String {
        val responseBody = response.bodyAsText()
        val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
        
        return chatResponse.choices.firstOrNull()?.message?.content
            ?: throw ApiException.EmptyResponse()
    }
    
    private fun parseJsonResponse(content: String): AiResponse {
        val cleanedContent = content.cleanJsonContent()
        return json.decodeFromString(cleanedContent)
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

    override fun close() {
        client.close()
    }
}

