package core.memory

import core.config.AppConfig
import core.domain.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

private val logger = LoggerFactory.getLogger(JsonMemoryStore::class.java)

/**
 * JSON-based implementation of MemoryStore.
 * Stores conversation history in a human-readable JSON file.
 */
class JsonMemoryStore(private val config: AppConfig) : MemoryStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val historyFile: File by lazy {
        val path = config.memoryStorePath
        val file = if (path != null && path.isNotEmpty()) {
            File(path)
        } else {
            File("ailearn.history.json")
        }
        logger.debug("Using JSON memory store file: ${file.absolutePath}")
        file
    }
    
    override suspend fun saveHistory(messages: List<Message>) {
        logger.debug("Saving ${messages.size} messages to JSON file: ${historyFile.absolutePath}")
        withContext(Dispatchers.IO) {
            try {
                val jsonContent = json.encodeToString(messages)
                Files.write(
                    historyFile.toPath(),
                    jsonContent.toByteArray(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
                logger.debug("Successfully saved ${messages.size} messages to JSON file")
            } catch (e: Exception) {
                logger.error("Error saving conversation history to JSON file: ${historyFile.absolutePath}", e)
            }
        }
    }
    
    override suspend fun loadHistory(): List<Message> {
        logger.debug("Loading history from JSON file: ${historyFile.absolutePath}")
        return withContext(Dispatchers.IO) {
            try {
                if (!historyFile.exists()) {
                    logger.debug("History file does not exist, returning empty list")
                    return@withContext emptyList()
                }
                
                val jsonContent = historyFile.readText()
                if (jsonContent.isBlank()) {
                    logger.debug("History file is empty, returning empty list")
                    return@withContext emptyList()
                }
                
                val messages = json.decodeFromString<List<Message>>(jsonContent)
                logger.info("Successfully loaded ${messages.size} messages from JSON file")
                messages
            } catch (e: Exception) {
                logger.error("Error loading conversation history from JSON file: ${historyFile.absolutePath}", e)
                emptyList()
            }
        }
    }
    
    override suspend fun clearHistory() {
        logger.debug("Clearing history from JSON file: ${historyFile.absolutePath}")
        withContext(Dispatchers.IO) {
            try {
                if (historyFile.exists()) {
                    historyFile.delete()
                    logger.info("History file deleted successfully")
                } else {
                    logger.debug("History file does not exist, nothing to clear")
                }
            } catch (e: Exception) {
                logger.error("Error clearing conversation history from JSON file: ${historyFile.absolutePath}", e)
            }
        }
    }
    
    override fun close() {
        // No resources to close for file-based storage
    }
}

