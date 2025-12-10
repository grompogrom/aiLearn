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
        add(Message.create(MessageRole.SYSTEM, Config.SYSTEM_PROMPT))
    }

    suspend fun sendRequest(userContent: String, temperature: Double = Config.TEMPERATURE): ApiResponse {
        // Определяем, является ли это ответом на вопрос ассистента
        val isAnswerToQuestion = messageHistory.lastOrNull()?.role == MessageRole.ASSISTANT.value
        val formattedUserContent = if (isAnswerToQuestion) {
            val questionNumber = getCurrentQuestionNumber()
            "[Ответ на вопрос $questionNumber]: $userContent"
        } else {
            userContent
        }
        
        // Добавляем сообщение пользователя в историю
        messageHistory[1] = (Message.create(MessageRole.USER, formattedUserContent))
        
        val request = createChatRequest(temperature)
        
        val response = client.post(Config.API_URL) {
            configureHeaders()
            setBody(request)
        }
        
        val apiResponse = extractContentFromResponse(response)
        
        // Добавляем ответ ассистента в историю
//        messageHistory.add(Message.create(MessageRole.ASSISTANT, apiResponse.content))
        
        return apiResponse
    }
    
    /**
     * Sends an independent request without using shared message history.
     * Useful for temperature comparison where each request should be independent.
     */
    suspend fun sendIndependentRequest(userContent: String, temperature: Double = Config.TEMPERATURE): ApiResponse {
        val messages = listOf(
            Message.create(MessageRole.SYSTEM, Config.SYSTEM_PROMPT),
            Message.create(MessageRole.USER, userContent)
        )
        
        val request = ChatRequest(
            model = Config.MODEL,
            messages = messages,
            max_tokens = Config.MAX_TOKENS,
            temperature = temperature
        )
        
        val response = client.post(Config.API_URL) {
            configureHeaders()
            setBody(request)
        }
        
        return extractContentFromResponse(response)
    }
    
    private fun createChatRequest(temperature: Double = Config.TEMPERATURE): ChatRequest {
        return ChatRequest(
            model = Config.MODEL,
            messages = messageHistory.toList(),
            max_tokens = Config.MAX_TOKENS,
            temperature = temperature
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
    
    private suspend fun extractContentFromResponse(response: HttpResponse): ApiResponse {
        val responseBody = response.bodyAsText()

        // Если API вернул ошибку (не 2xx), показываем тело ответа напрямую
        if (!response.status.isSuccess()) {
            val errorContent = buildString {
                appendLine("Ошибка от API (${response.status.value} ${response.status.description}):")
                appendLine(responseBody)
            }
            return ApiResponse(content = errorContent, usage = null)
        }

        // Пытаемся распарсить успешный ответ как ChatResponse
        return try {
            val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
            val content = chatResponse.choices.firstOrNull()?.message?.content
                ?: responseBody
            ApiResponse(content = content, usage = chatResponse.usage)
        } catch (e: Exception) {
            ApiResponse(content = responseBody, usage = null)
        }
    }

    override fun close() {
        client.close()
    }
}

