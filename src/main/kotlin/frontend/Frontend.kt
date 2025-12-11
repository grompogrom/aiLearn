package frontend

import core.conversation.ConversationManager
import core.domain.ChatResponse

/**
 * Interface for frontend implementations (CLI, GUI, HTTP API, etc.).
 * This abstraction allows different frontends to be plugged in without changing core logic.
 */
interface Frontend {
    /**
     * Starts the frontend and begins interaction with the user.
     * @param conversationManager The conversation manager to use for chat requests
     */
    suspend fun start(conversationManager: ConversationManager)
}

/**
 * Represents user input from the frontend.
 */
data class UserInput(
    val content: String,
    val isExit: Boolean = false
)

/**
 * Represents output to display to the user.
 */
data class UserOutput(
    val content: String,
    val tokenUsage: String? = null,
    val isDialogEnd: Boolean = false
)
