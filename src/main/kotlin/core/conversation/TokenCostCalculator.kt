package core.conversation

import core.config.AppConfig
import core.domain.TokenUsage

/**
 * Calculates token costs based on usage statistics.
 */
class TokenCostCalculator(private val config: AppConfig) {
    fun calculatePrice(totalTokens: Int): Double {
        return (totalTokens / 1_000_000.0) * config.pricePerMillionTokens
    }

    fun formatTokenUsage(usage: TokenUsage?): String {
        if (usage == null) return ""
        
        return buildString {
            appendLine("\n--- Token Usage ---")
            usage.promptTokens?.let { appendLine("Prompt tokens: $it") }
            usage.completionTokens?.let { appendLine("Completion tokens: $it") }
            usage.totalTokens?.let {
                appendLine("Total tokens: $it")
                val price = calculatePrice(it)
                appendLine("Price: $${String.format("%.6f", price)}")
            }
            appendLine("------------------\n")
        }
    }
}
