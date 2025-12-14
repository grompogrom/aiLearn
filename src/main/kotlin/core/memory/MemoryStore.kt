package core.memory

import core.domain.Message

/**
 * Interface for persistent storage of conversation history.
 * Implementations can use JSON, SQLite, or other storage backends.
 */
interface MemoryStore : AutoCloseable {
    /**
     * Saves the conversation history.
     * @param messages The list of messages to save
     */
    suspend fun saveHistory(messages: List<Message>)
    
    /**
     * Loads the conversation history.
     * @return The list of messages, or empty list if no history exists
     */
    suspend fun loadHistory(): List<Message>
    
    /**
     * Clears the stored conversation history.
     */
    suspend fun clearHistory()
}

