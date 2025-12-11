package core.domain

/**
 * Enum representing the role of a message in a conversation.
 */
enum class MessageRole(val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant")
}
