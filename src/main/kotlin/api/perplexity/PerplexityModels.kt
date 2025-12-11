package api.perplexity

import kotlinx.serialization.Serializable

/**
 * API-specific models for Perplexity API.
 * These are separate from core domain models to allow for provider-specific differences.
 */

@Serializable
data class PerplexityMessage(
    val role: String,
    val content: String,
    val disable_search: Boolean = true
)

@Serializable
data class PerplexityChatRequest(
    val model: String,
    val messages: List<PerplexityMessage>,
    val max_tokens: Int = 1000,
    val temperature: Double = 0.3
)

@Serializable
data class PerplexityUsage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null,
    val search_context_size: String? = null,
    val citation_tokens: Int? = null,
    val num_search_queries: Int? = null,
    val reasoning_tokens: Int? = null
)

@Serializable
data class PerplexityChoiceMessage(
    val content: String,
    val role: String
)

@Serializable
data class PerplexityChoice(
    val index: Int,
    val finish_reason: String,
    val message: PerplexityChoiceMessage
)

@Serializable
data class PerplexitySearchResult(
    val title: String? = null,
    val url: String? = null,
    val date: String? = null
)

@Serializable
data class PerplexityVideo(
    val url: String? = null,
    val thumbnail_url: String? = null,
    val thumbnail_width: Int? = null,
    val thumbnail_height: Int? = null,
    val duration: Int? = null
)

@Serializable
data class PerplexityChatResponse(
    val id: String? = null,
    val model: String? = null,
    val created: Long? = null,
    val usage: PerplexityUsage? = null,
    val `object`: String? = null,
    val choices: List<PerplexityChoice>,
    val search_results: List<PerplexitySearchResult>? = null,
    val videos: List<PerplexityVideo>? = null
)
