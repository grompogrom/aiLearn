import kotlinx.serialization.Serializable

@Serializable
data class AiResponse(
    val answer: String,
    val recomendation: String
)

