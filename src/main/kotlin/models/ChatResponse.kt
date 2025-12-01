import kotlinx.serialization.Serializable

@Serializable
data class Usage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null,
    val search_context_size: String? = null,
    val citation_tokens: Int? = null,
    val num_search_queries: Int? = null,
    val reasoning_tokens: Int? = null
)

@Serializable
data class ChoiceMessage(
    val content: String,
    val role: String
)

@Serializable
data class Choice(
    val index: Int,
    val finish_reason: String,
    val message: ChoiceMessage
)

@Serializable
data class SearchResult(
    val title: String? = null,
    val url: String? = null,
    val date: String? = null
)

@Serializable
data class Video(
    val url: String? = null,
    val thumbnail_url: String? = null,
    val thumbnail_width: Int? = null,
    val thumbnail_height: Int? = null,
    val duration: Int? = null
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val created: Long? = null,
    val usage: Usage? = null,
    val `object`: String? = null,
    val choices: List<Choice>,
    val search_results: List<SearchResult>? = null,
    val videos: List<Video>? = null
)

