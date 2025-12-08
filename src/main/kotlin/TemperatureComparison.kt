import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Script that requests API with different temperatures for a list of prompts,
 * saves full responses to JSON, then parses and displays results in a formatted table.
 */
fun main() = runBlocking {
    // List of prompts to test
    val prompts = listOf(
        "Сколько дней в феврале в невисокосном году?",
        "Сколько будет 27 × 14?",
        "Объясни за 2–3 предложения, что такое энтропия",
        "Придумай необычную идею для стартапа в сфере экологии (одна короткая идея)",
        "Напиши короткий (3–6 строк) пример кода на Python, который считывает число и печатает его квадрат",
        "Как за 30 минут улучшить концентрацию перед работой?",
        "Переведи на русский: «I'm feeling under the weather» — формально и неформально"
    )

        val jsonFile = File("temperature_comparison.json")
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    // Store results with full responses (no truncation)
    val results = mutableListOf<TableRow>()
    
    println("Making API requests with different temperatures...")
    println("This may take a while...\n")
    
    ApiClient().use { apiClient ->
        prompts.forEachIndexed { index, prompt ->
            println("Processing prompt ${index + 1}/${prompts.size}: ${prompt.take(50)}...")
            
            val row = TableRow(
                question = prompt,
                tempA = "",
                tempB = "",
                tempC = ""
            )
            
            // Request with temperature 0.0
            try {
                val response0 = apiClient.sendIndependentRequest(prompt, temperature = 0.0)
                row.tempA = response0 // Full response, no truncation
            } catch (e: Exception) {
                row.tempA = "Error: ${e.message}"
            }
            
            // Request with temperature 0.7
            try {
                val response07 = apiClient.sendIndependentRequest(prompt, temperature = 0.7)
                row.tempB = response07 // Full response, no truncation
            } catch (e: Exception) {
                row.tempB = "Error: ${e.message}"
            }
            
            // Request with temperature 1.2
            try {
                val response12 = apiClient.sendIndependentRequest(prompt, temperature = 1.2)
                row.tempC = response12 // Full response, no truncation
            } catch (e: Exception) {
                row.tempC = "Error: ${e.message}"
            }
            
            results.add(row)
        }
    }
    
    // Save full responses to JSON
    val jsonData = TemperatureComparisonData(results)
    jsonFile.writeText(json.encodeToString(jsonData))
    println("\nFull responses saved to: ${jsonFile.absolutePath}\n")
    
    // Parse JSON and display table
    val loadedData = json.decodeFromString<TemperatureComparisonData>(jsonFile.readText())
    printTable(loadedData.results)
}

@Serializable
data class TableRow(
    val question: String,
    var tempA: String,
    var tempB: String,
    var tempC: String
)

@Serializable
data class TemperatureComparisonData(
    val results: List<TableRow>
)

fun printTable(rows: List<TableRow>) {
    // Set reasonable column widths for terminal display (full responses will wrap)
    val questionColWidth = 40
    val temp0ColWidth = 50
    val temp07ColWidth = 50
    val temp12ColWidth = 50
    
    // Print header
    val headerSeparator = "+${"-".repeat(questionColWidth)}+${"-".repeat(temp0ColWidth)}+${"-".repeat(temp07ColWidth)}+${"-".repeat(temp12ColWidth)}+"
    println(headerSeparator)
    println("|${"question".padEnd(questionColWidth)}|${"temperature 0".padEnd(temp0ColWidth)}|${"temperature 0.7".padEnd(temp07ColWidth)}|${"temperature 1.2".padEnd(temp12ColWidth)}|")
    println(headerSeparator)
    
    // Print rows with full responses (wrapped, not truncated)
    rows.forEach { row ->
        val questionLines = wrapText(row.question, questionColWidth)
        val temp0Lines = wrapText(row.tempA, temp0ColWidth)
        val temp07Lines = wrapText(row.tempB, temp07ColWidth)
        val temp12Lines = wrapText(row.tempC, temp12ColWidth)
        
        val maxLines = maxOf(questionLines.size, temp0Lines.size, temp07Lines.size, temp12Lines.size)
        
        for (i in 0 until maxLines) {
            val questionText = questionLines.getOrElse(i) { "" }.padEnd(questionColWidth)
            val temp0Text = temp0Lines.getOrElse(i) { "" }.padEnd(temp0ColWidth)
            val temp07Text = temp07Lines.getOrElse(i) { "" }.padEnd(temp07ColWidth)
            val temp12Text = temp12Lines.getOrElse(i) { "" }.padEnd(temp12ColWidth)
            
            println("|$questionText|$temp0Text|$temp07Text|$temp12Text|")
        }
        
        println(headerSeparator)
    }
}

fun wrapText(text: String, maxWidth: Int): List<String> {
    if (text.isEmpty()) return listOf("")
    
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""
    
    words.forEach { word ->
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (testLine.length <= maxWidth) {
            currentLine = testLine
        } else {
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
            currentLine = if (word.length > maxWidth) {
                lines.add(word.take(maxWidth))
                word.drop(maxWidth)
            } else {
                word
            }
        }
    }
    
    if (currentLine.isNotEmpty()) {
        lines.add(currentLine)
    }
    
    return lines.ifEmpty { listOf("") }
}

