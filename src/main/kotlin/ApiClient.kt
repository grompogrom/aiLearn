import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.pogrom.BuildConfig

class ApiClient {
    companion object {
        const val API_URL = "https://api.perplexity.ai/chat/completions"
        val API_KEY: String = BuildConfig.API_KEY
        const val MODEL = "sonar"
    }

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
            try {
                val request = ChatRequest(
                    model = MODEL,
                    messages = listOf(
                        Message(
                            role = "user",
                            content = userContent,
                        )
                    ),
                    max_tokens = 100
                )
                
                val response = client.post(API_URL) {
                    header("accept", "application/json")
                    header("content-type", "application/json")
                    header("Authorization", "Bearer $API_KEY")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                
                val responseBody = response.bodyAsText()
                val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
                
                // Извлекаем content из ответа
                val content = chatResponse.choices.firstOrNull()?.message?.content
                    ?: throw RuntimeException("Ответ не содержит содержимого")
                
                content
            } catch (e: Exception) {
                throw RuntimeException("Ошибка при отправке запроса: ${e.message}", e)
            }
        }
    }

    fun close() {
        client.close()
    }
}

