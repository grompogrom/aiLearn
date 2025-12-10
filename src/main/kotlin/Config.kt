import org.pogrom.BuildConfig

object Config {
    const val API_URL = "https://api.perplexity.ai/chat/completions"
    val API_KEY: String = BuildConfig.API_KEY
    const val MODEL = "sonar"
    const val MAX_TOKENS = 500
    const val TEMPERATURE = 0.6
    val SYSTEM_PROMPT = """Answers must be short and succinct"""
    
    const val DIALOG_END_MARKER = "###END###"
    const val PRICE_MILLION_TOKENS = 1.0 // Price per million tokens (update with actual pricing)
}