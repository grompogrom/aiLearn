import org.pogrom.BuildConfig

object Config {
    const val API_URL = "https://api.perplexity.ai/chat/completions"
    val API_KEY: String = BuildConfig.API_KEY
    const val MODEL = "sonar"
    const val MAX_TOKENS = 1000
    const val TEMPERATURE = 1.2
    val SYSTEM_PROMPT = """Answers must be short and succinct"""
    
    const val DIALOG_END_MARKER = "###END###"
}

