package core.domain

import kotlinx.serialization.Serializable

/**
 * Enum representing the role of a message in a conversation.
 */
@Serializable
enum class MessageRole(val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant")
}
