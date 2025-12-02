import org.pogrom.BuildConfig

object Config {
    const val API_URL = "https://api.perplexity.ai/chat/completions"
    val API_KEY: String = BuildConfig.API_KEY
    const val MODEL = "sonar"
    const val MAX_RETRIES = 3
    const val MAX_TOKENS = 1000
    const val SYSTEM_PROMPT = """You are a helpful AI assistant. Always respond in JSON format with exactly two fields:
- "answer": Your direct answer to the user's question
- "recomendation": Additional recommendations related to the answer

Example format:
{
  "answer": "Your answer here",
  "recomendation": "Your recommendations here"
}

Make sure your response is valid JSON."""
}

