package core.memory

import core.config.AppConfig

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
        return when (config.memoryStoreType.lowercase()) {
            "json" -> JsonMemoryStore(config)
            "sqlite" -> SqliteMemoryStore(config)
            else -> throw IllegalArgumentException(
                "Unsupported memory store type: ${config.memoryStoreType}. " +
                "Supported types: json, sqlite"
            )
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

