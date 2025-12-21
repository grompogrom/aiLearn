package core.memory

import core.config.AppConfig
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(MemoryStoreFactory::class.java)

/**
 * Factory for creating MemoryStore instances based on configuration.
 */
object MemoryStoreFactory {
    /**
     * Creates a MemoryStore instance based on the configuration.
     * @param config Application configuration
     * @return MemoryStore instance
     * @throws IllegalArgumentException if memoryStoreType is not supported
     */
    fun create(config: AppConfig): MemoryStore {
        logger.debug("Creating memory store (type: ${config.memoryStoreType})")
        return when (config.memoryStoreType.lowercase()) {
            "json" -> {
                logger.info("Creating JSON memory store")
                JsonMemoryStore(config)
            }
            "sqlite" -> {
                logger.info("Creating SQLite memory store")
                SqliteMemoryStore(config)
            }
            else -> {
                logger.error("Unsupported memory store type: ${config.memoryStoreType}")
                throw IllegalArgumentException(
                    "Unsupported memory store type: ${config.memoryStoreType}. " +
                    "Supported types: json, sqlite"
                )
            }
        }
    }
}

/**
 * Enum representing supported memory store types.
 */
enum class MemoryStoreType {
    JSON,
    SQLITE
}

