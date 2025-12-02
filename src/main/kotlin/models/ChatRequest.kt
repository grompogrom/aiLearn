import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,
    val content: String,
    val disable_search: Boolean = true
) {
    companion object {
        fun create(role: MessageRole, content: String, disableSearch: Boolean = true) = Message(
            role = role.value,
            content = content,
            disable_search = disableSearch
        )
    }
}

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int = 1000
)

