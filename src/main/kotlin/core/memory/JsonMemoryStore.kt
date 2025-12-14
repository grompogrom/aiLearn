package core.memory

import core.config.AppConfig
import core.domain.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

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
        if (path != null && path.isNotEmpty()) {
            File(path)
        } else {
            File("ailearn.history.json")
        }
    }
    
    override suspend fun saveHistory(messages: List<Message>) {
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
            } catch (e: Exception) {
                System.err.println("Error saving conversation history: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    override suspend fun loadHistory(): List<Message> {
        return withContext(Dispatchers.IO) {
            try {
                if (!historyFile.exists()) {
                    return@withContext emptyList()
                }
                
                val jsonContent = historyFile.readText()
                if (jsonContent.isBlank()) {
                    return@withContext emptyList()
                }
                
                json.decodeFromString<List<Message>>(jsonContent)
            } catch (e: Exception) {
                System.err.println("Error loading conversation history: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    override suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            try {
                if (historyFile.exists()) {
                    historyFile.delete()
                }
            } catch (e: Exception) {
                System.err.println("Error clearing conversation history: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    override fun close() {
        // No resources to close for file-based storage
    }
}

