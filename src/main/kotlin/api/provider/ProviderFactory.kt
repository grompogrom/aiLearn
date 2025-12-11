package api.provider

import api.perplexity.PerplexityProvider
import core.config.AppConfig
import core.provider.LlmProvider

/**
 * Factory for creating LLM provider instances.
 * This allows easy switching between different providers.
 */
object ProviderFactory {
    enum class ProviderType {
        PERPLEXITY,
        // Future: OPENAI, GOOGLE, etc.
    }

    fun create(type: ProviderType, config: AppConfig): LlmProvider {
        return when (type) {
            ProviderType.PERPLEXITY -> PerplexityProvider(config)
            // Future implementations:
            // ProviderType.OPENAI -> OpenAIProvider(config)
            // ProviderType.GOOGLE -> GoogleProvider(config)
        }
    }

    /**
     * Creates a provider based on configuration or defaults to Perplexity.
     */
    fun createFromConfig(config: AppConfig): LlmProvider {
        // Could read provider type from config in the future
        return create(ProviderType.PERPLEXITY, config)
    }
}
