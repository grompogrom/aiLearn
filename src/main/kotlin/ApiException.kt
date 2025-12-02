sealed class ApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class InvalidJsonResponse(
        val attempts: Int,
        val lastContent: String?,
        cause: Throwable
    ) : ApiException(
        buildString {
            appendLine("Не удалось получить ответ в формате JSON после $attempts попыток.")
            lastContent?.let {
                appendLine("Последний ответ:")
                appendLine(it)
            }
            appendLine()
            appendLine("Ошибка парсинга: ${cause.message}")
        },
        cause
    )
    
    class EmptyResponse : ApiException("Ответ не содержит содержимого")
    
    class RequestFailed(message: String, cause: Throwable? = null) : ApiException(message, cause)
}

