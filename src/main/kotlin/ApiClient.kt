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
        // Увеличиваем таймаут запроса, чтобы снизить вероятность ошибок Request timeout
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 60_000
        }
    }

    private val messageHistory = mutableListOf<Message>().apply {
        add(Message.create(MessageRole.SYSTEM, Config.SYSTEM_PROMPT))
    }

    suspend fun sendRequest(userContent: String): String {
        // Определяем, является ли это ответом на вопрос ассистента
        val isAnswerToQuestion = messageHistory.lastOrNull()?.role == MessageRole.ASSISTANT.value
        val formattedUserContent = if (isAnswerToQuestion) {
            val questionNumber = getCurrentQuestionNumber()
            "[Ответ на вопрос $questionNumber]: $userContent"
        } else {
            userContent
        }
        
        // Добавляем сообщение пользователя в историю
        messageHistory.add(Message.create(MessageRole.USER, formattedUserContent))
        
        val request = createChatRequest()
        
        val response = client.post(Config.API_URL) {
            configureHeaders()
            setBody(request)
        }
        
        val assistantResponse = extractContentFromResponse(response)
        
        // Добавляем ответ ассистента в историю
        messageHistory.add(Message.create(MessageRole.ASSISTANT, assistantResponse))
        
        return assistantResponse
    }
    
    private fun createChatRequest(): ChatRequest {
        return ChatRequest(
            model = Config.MODEL,
            messages = messageHistory.toList(),
            max_tokens = Config.MAX_TOKENS
        )
    }
    
    private fun getCurrentQuestionNumber(): Int {
        // Считаем количество вопросов ассистента (сообщения ASSISTANT после системного)
        var questionCount = 0
        for (message in messageHistory) {
            if (message.role == MessageRole.ASSISTANT.value) {
                questionCount++
            }
        }
        return questionCount
    }

    private fun HttpRequestBuilder.configureHeaders() {
        header("accept", "application/json")
        header("content-type", "application/json")
        header("Authorization", "Bearer ${Config.API_KEY}")
        contentType(ContentType.Application.Json)
    }
    
    private suspend fun extractContentFromResponse(response: HttpResponse): String {
        val responseBody = response.bodyAsText()

        // Если API вернул ошибку (не 2xx), показываем тело ответа напрямую
        if (!response.status.isSuccess()) {
            return buildString {
                appendLine("Ошибка от API (${response.status.value} ${response.status.description}):")
                appendLine(responseBody)
            }
        }

        // Пытаемся распарсить успешный ответ как ChatResponse
        return try {
            val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
            chatResponse.choices.firstOrNull()?.message?.content
                ?: responseBody
        } catch (e: Exception) {
            responseBody
        }
    }

    override fun close() {
        client.close()
    }
}

