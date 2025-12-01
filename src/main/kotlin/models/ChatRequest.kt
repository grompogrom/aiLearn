import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,
    val content: String,
    val disable_search: Boolean = true
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int = 1000
)

