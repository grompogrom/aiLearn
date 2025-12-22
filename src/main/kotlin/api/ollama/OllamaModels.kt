package api.ollama

import kotlinx.serialization.Serializable

/**
 * Request model for Ollama embed API.
 */
@Serializable
data class EmbedRequest(
    val model: String,
    val input: List<String>
)

/**
 * Response model from Ollama embed API.
 */
@Serializable
data class EmbedResponse(
    val model: String,
    val embeddings: List<List<Float>>,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null
)

