package core.reminder

import core.conversation.ConversationManager
import core.mcp.McpResult
import core.mcp.McpService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Callback type for reminder check results.
 * Called when a reminder check completes with the LLM response.
 */
typealias ReminderCheckCallback = (String) -> Unit

/**
 * Service that periodically checks reminders by calling the reminder.list MCP tool,
 * sending the results to LLM for description, and printing the output.
 * 
 * This service runs in a background coroutine and does not save its interactions
 * to conversation history.
 */
class ReminderChecker(
    private val conversationManager: ConversationManager,
    private val mcpService: McpService?,
    private val outputCallback: ReminderCheckCallback? = null
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    @Volatile private var isRunning = false
    
    /**
     * Checks if the reminder checker is currently running.
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Starts the periodic reminder checking.
     * Checks reminders every 60 seconds.
     */
    fun start() {
        if (isRunning) {
            return
        }
        isRunning = true
        
        scope.launch {
            while (isActive && isRunning) {
                try {
                    checkReminders()
                } catch (e: Exception) {
                    // Log error but continue running
                    System.err.println("Warning: Reminder check failed: ${e.message}")
                }
                
                // Wait 60 seconds before next check
                delay(60_000L)
            }
        }
    }
    
    /**
     * Stops the periodic reminder checking.
     */
    fun stop() {
        isRunning = false
    }
    
    /**
     * Toggles the reminder checker on/off.
     * @return true if reminder checker is now running, false otherwise
     */
    fun toggle(): Boolean {
        if (isRunning) {
            stop()
        } else {
            start()
        }
        return isRunning
    }
    
    /**
     * Performs a single reminder check:
     * 1. Calls reminder.list via MCP
     * 2. Sends result to LLM for description
     * 3. Prints the LLM response
     */
    private suspend fun checkReminders() {
        if (mcpService == null) {
            // MCP service not available, skip check
            return
        }
        
        // Call reminder.list tool
        val toolResult = mcpService.callTool("reminder.list", "{}")
        
        when (toolResult) {
            is McpResult.Success -> {
                val reminderData = toolResult.value
                
                // Build prompt for LLM
                val prompt = buildString {
                    append("опиши события описанные здесь. Сделай это в стиле гопника. БЕЗ МАТА\n\n")
                    append(reminderData)
                }
                
                // Send to LLM without saving to history
                val response = conversationManager.sendRequestWithoutHistory(prompt)
                
                // Print the response via callback
                outputCallback?.invoke(response.content)
            }
            is McpResult.Error -> {
                // Log error but don't print to user (silent failure)
                System.err.println("Warning: Failed to call reminder.list: ${toolResult.error}")
            }
        }
    }
}

