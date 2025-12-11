package core.provider

import core.domain.ChatRequest
import core.domain.ChatResponse

/**
 * Interface for LLM providers (OpenAI, Google, Perplexity, etc.).
 * This abstraction allows different providers to be plugged in without changing core logic.
 */
interface LlmProvider : AutoCloseable {
    /**
     * Sends a chat request to the LLM provider and returns the response.
     *
     * @param request The chat request containing messages and parameters
     * @return ChatResponse with content and token usage
     * @throws LlmProviderException if the request fails
     */
    suspend fun sendRequest(request: ChatRequest): ChatResponse
}

/**
 * Base exception for LLM provider errors.
 */
sealed class LlmProviderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidResponse(message: String, cause: Throwable? = null) : LlmProviderException(message, cause)
    class EmptyResponse : LlmProviderException("Response does not contain content")
    class RequestFailed(message: String, cause: Throwable? = null) : LlmProviderException(message, cause)
    class ConfigurationError(message: String) : LlmProviderException(message)
}
