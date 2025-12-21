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
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(PerplexityProvider::class.java)

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
        logger.debug("Sending LLM request (model: ${request.model}, messages: ${request.messages.size}, maxTokens: ${request.maxTokens})")
        val apiRequest = PerplexityAdapter.toApiRequest(request)
        
        val response = try {
            logger.trace("POST request to ${config.apiUrl}")
            client.post(config.apiUrl) {
                configureHeaders()
                setBody(apiRequest)
            }
        } catch (e: Exception) {
            logger.error("Failed to send request to Perplexity API", e)
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
        logger.trace("Received response with status: ${response.status}")

        if (!response.status.isSuccess()) {
            val errorMessage = buildString {
                appendLine("API Error (${response.status.value} ${response.status.description}):")
                appendLine(responseBody)
            }
            logger.error("Perplexity API returned error: ${response.status.value} ${response.status.description}")
            throw LlmProviderException.RequestFailed(errorMessage)
        }

        return try {
            val perplexityResponse = json.decodeFromString<PerplexityChatResponse>(responseBody)
            val domainResponse = PerplexityAdapter.toDomainResponse(perplexityResponse)
            
            if (domainResponse.content.isBlank()) {
                logger.warn("Received empty response from Perplexity API")
                throw LlmProviderException.EmptyResponse()
            }
            
            logger.debug("Successfully received response (content length: ${domainResponse.content.length}, usage: ${domainResponse.usage})")
            domainResponse
        } catch (e: LlmProviderException) {
            logger.error("LLM provider exception: ${e::class.simpleName}", e)
            throw e
        } catch (e: Exception) {
            logger.error("Failed to parse Perplexity API response", e)
            throw LlmProviderException.InvalidResponse(
                "Failed to parse response: ${e.message}",
                e
            )
        }
    }

    override fun close() {
        logger.debug("Closing Perplexity provider")
        client.close()
        logger.info("Perplexity provider closed")
    }
}
