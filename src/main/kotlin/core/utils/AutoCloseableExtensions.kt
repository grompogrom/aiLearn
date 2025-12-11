package core.utils

/**
 * Extension function to use AutoCloseable with a use block, similar to Closeable.use.
 */
inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        this.close()
    }
}
