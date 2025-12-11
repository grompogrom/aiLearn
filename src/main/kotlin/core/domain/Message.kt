package core.domain

/**
 * Represents a message in a conversation with role and content.
 */
data class Message(
    val role: MessageRole,
    val content: String,
    val disableSearch: Boolean = true
) {
    companion object {
        fun create(role: MessageRole, content: String, disableSearch: Boolean = true) = Message(
            role = role,
            content = content,
            disableSearch = disableSearch
        )
    }
}
