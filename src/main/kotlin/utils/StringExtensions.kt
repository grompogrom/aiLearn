fun String.cleanJsonContent(): String {
    return this
        .replace(Regex("```json\\s*"), "")
        .replace(Regex("```\\s*"), "")
        .trim()
}

fun Int.getRetryWord(): String {
    return when {
        this % 10 == 1 && this % 100 != 11 -> "ретрай"
        this % 10 in 2..4 && this % 100 !in 12..14 -> "ретрая"
        else -> "ретраев"
    }
}

