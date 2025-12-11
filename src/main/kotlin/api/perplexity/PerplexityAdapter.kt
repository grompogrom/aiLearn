package api.perplexity

import core.domain.ChatRequest
import core.domain.ChatResponse
import core.domain.Message
import core.domain.TokenUsage

/**
 * Adapter for converting between core domain models and Perplexity API models.
 */
object PerplexityAdapter {
    fun toApiRequest(request: ChatRequest): PerplexityChatRequest {
        return PerplexityChatRequest(
            model = request.model,
            messages = request.messages.map { toApiMessage(it) },
            max_tokens = request.maxTokens,
            temperature = request.temperature
        )
    }

    fun toDomainResponse(response: PerplexityChatResponse): ChatResponse {
        val content = response.choices.firstOrNull()?.message?.content ?: ""
        val usage = response.usage?.let { toDomainUsage(it) }
        return ChatResponse(content = content, usage = usage)
    }

    private fun toApiMessage(message: Message): PerplexityMessage {
        return PerplexityMessage(
            role = message.role.value,
            content = message.content,
            disable_search = message.disableSearch
        )
    }

    private fun toDomainUsage(usage: PerplexityUsage): TokenUsage {
        return TokenUsage(
            promptTokens = usage.prompt_tokens,
            completionTokens = usage.completion_tokens,
            totalTokens = usage.total_tokens,
            searchContextSize = usage.search_context_size,
            citationTokens = usage.citation_tokens,
            numSearchQueries = usage.num_search_queries,
            reasoningTokens = usage.reasoning_tokens
        )
    }
}
