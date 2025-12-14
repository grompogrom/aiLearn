package core.memory

import core.config.AppConfig
import core.domain.Message
import core.domain.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * SQLite-based implementation of MemoryStore.
 * Stores conversation history in a SQLite database.
 */
class SqliteMemoryStore(private val config: AppConfig) : MemoryStore {
    private val dbFile: File by lazy {
        val path = config.memoryStorePath
        if (path != null && path.isNotEmpty()) {
            File(path)
        } else {
            File("ailearn.history.db")
        }
    }
    
    private val connection: Connection by lazy {
        initializeDatabase()
    }
    
    private fun initializeDatabase(): Connection {
        val conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        
        // Create table if it doesn't exist
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS conversation_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    disable_search INTEGER NOT NULL DEFAULT 1,
                    timestamp INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                )
            """.trimIndent())
        }
        
        return conn
    }
    
    override suspend fun saveHistory(messages: List<Message>) {
        withContext(Dispatchers.IO) {
            try {
                connection.autoCommit = false
                
                // Clear existing history
                connection.prepareStatement("DELETE FROM conversation_history").use { stmt ->
                    stmt.executeUpdate()
                }
                
                // Insert new messages
                val insertStmt = connection.prepareStatement(
                    "INSERT INTO conversation_history (role, content, disable_search, timestamp) VALUES (?, ?, ?, ?)"
                )
                
                try {
                    messages.forEachIndexed { index, message ->
                        insertStmt.setString(1, message.role.value)
                        insertStmt.setString(2, message.content)
                        insertStmt.setInt(3, if (message.disableSearch) 1 else 0)
                        insertStmt.setLong(4, System.currentTimeMillis() / 1000 + index) // Preserve order
                        insertStmt.addBatch()
                    }
                    insertStmt.executeBatch()
                    connection.commit()
                } finally {
                    insertStmt.close()
                }
            } catch (e: SQLException) {
                connection.rollback()
                System.err.println("Error saving conversation history: ${e.message}")
                e.printStackTrace()
            } finally {
                connection.autoCommit = true
            }
        }
    }
    
    override suspend fun loadHistory(): List<Message> {
        return withContext(Dispatchers.IO) {
            try {
                val messages = mutableListOf<Message>()
                
                connection.prepareStatement(
                    "SELECT role, content, disable_search FROM conversation_history ORDER BY timestamp ASC"
                ).use { stmt ->
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val roleStr = rs.getString("role")
                        val role = MessageRole.values().find { it.value == roleStr }
                            ?: MessageRole.USER // Fallback
                        val content = rs.getString("content")
                        val disableSearch = rs.getInt("disable_search") == 1
                        
                        messages.add(Message(role, content, disableSearch))
                    }
                }
                
                messages
            } catch (e: SQLException) {
                System.err.println("Error loading conversation history: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    override suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            try {
                connection.prepareStatement("DELETE FROM conversation_history").use { stmt ->
                    stmt.executeUpdate()
                }
            } catch (e: SQLException) {
                System.err.println("Error clearing conversation history: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    override fun close() {
        try {
            if (!connection.isClosed) {
                connection.close()
            }
        } catch (e: SQLException) {
            System.err.println("Error closing database connection: ${e.message}")
            e.printStackTrace()
        }
    }
}

