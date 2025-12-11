package api.perplexity

import core.config.AppConfig
import core.domain.ChatRequest
import core.domain.ChatResponse
import core.provider.LlmProvider
import core.provider.LlmProviderException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Perplexity AI LLM provider implementation.
 */
class PerplexityProvider(
    private val config: AppConfig
) : LlmProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMillis
        }
    }

    override suspend fun sendRequest(request: ChatRequest): ChatResponse {
        val apiRequest = PerplexityAdapter.toApiRequest(request)
        
        val response = try {
            client.post(config.apiUrl) {
                configureHeaders()
                setBody(apiRequest)
            }
        } catch (e: Exception) {
            throw LlmProviderException.RequestFailed(
                "Failed to send request to Perplexity API: ${e.message}",
                e
            )
        }

        return extractResponse(response)
    }

    private fun HttpRequestBuilder.configureHeaders() {
        header("accept", "application/json")
        header("content-type", "application/json")
        header("Authorization", "Bearer ${config.apiKey}")
        contentType(ContentType.Application.Json)
    }

    private suspend fun extractResponse(response: HttpResponse): ChatResponse {
        val responseBody = response.bodyAsText()

        if (!response.status.isSuccess()) {
            val errorMessage = buildString {
                appendLine("API Error (${response.status.value} ${response.status.description}):")
                appendLine(responseBody)
            }
            throw LlmProviderException.RequestFailed(errorMessage)
        }

        return try {
            val perplexityResponse = json.decodeFromString<PerplexityChatResponse>(responseBody)
            val domainResponse = PerplexityAdapter.toDomainResponse(perplexityResponse)
            
            if (domainResponse.content.isBlank()) {
                throw LlmProviderException.EmptyResponse()
            }
            
            domainResponse
        } catch (e: LlmProviderException) {
            throw e
        } catch (e: Exception) {
            throw LlmProviderException.InvalidResponse(
                "Failed to parse response: ${e.message}",
                e
            )
        }
    }

    override fun close() {
        client.close()
    }
}
