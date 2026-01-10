# RAG History Integration - Implementation Summary

## Overview

Successfully implemented RAG mode with conversation history support. The system now:
1. Stores conversation history when RAG mode is enabled
2. Uses the last 5 messages from history to build context-aware embeddings
3. Ensures retrieved chunks are NOT stored in history (only user messages and LLM answers)

## Changes Made

### 1. Configuration (`core/config/AppConfig.kt` & `ConfigLoader.kt`)

Added new configuration parameter:
- `ragHistoryContextSize: Int` - Number of recent messages to use for RAG context (default: 5)
- Environment variable: `AILEARN_RAG_HISTORY_CONTEXT_SIZE`
- Config file property: `rag.history.context.size`

### 2. ConversationManager (`core/conversation/ConversationManager.kt`)

Added new method `sendRequestWithRag()`:
```kotlin
suspend fun sendRequestWithRag(
    userContent: String,
    ragQueryService: RagQueryService,
    temperature: Double? = null
): ChatResponse
```

**Flow**:
1. Adds user message to conversation history
2. Extracts last N messages (configurable, default 5) excluding system prompts
3. Calls `ragQueryService.queryWithHistory()` with recent messages
4. Adds only the LLM answer to history (NOT the chunks)
5. Returns ChatResponse with answer

Added helper method `getRecentMessages()`:
- Filters out system prompts
- Returns last N messages from history

### 3. RagQueryService (`core/rag/RagQueryService.kt`)

Added new method `queryWithHistory()`:
```kotlin
suspend fun queryWithHistory(
    question: String,
    recentMessages: List<Message>,
    topK: Int = 3
): QueryResult
```

**Pipeline**:
1. Load RAG index
2. **Concatenate recent messages with current question**:
   ```
   [Message 1 content]
   [Message 2 content]
   ...
   [Message N content]
   Current question: [user question]
   ```
3. Generate single embedding for combined text
4. Use this embedding for similarity search
5. Continue with existing pipeline (re-ranking, filtering, LLM generation)

Refactored shared logic into `executeQueryPipeline()`:
- Both `query()` and `queryWithHistory()` use this shared pipeline
- Reduces code duplication
- Maintains consistency

### 4. CliFrontend (`frontend/cli/CliFrontend.kt`)

Updated `handleUserRequest()`:
- When `ragEnabled` is true, routes through `conversationManager.sendRequestWithRag()`
- Removed direct call to `ragQueryService.query()` for RAG mode
- Maintains backward compatibility for one-time queries (`/ask` and `/rag <question>`)

Removed `handleRagQuery()` method (no longer needed).

### 5. Test Updates

Updated all test files with mock `AppConfig` to include `ragHistoryContextSize`:
- `ConversationManagerSummarizationTest.kt`
- `SqliteMemoryStoreTest.kt`
- `MemoryStoreFactoryTest.kt`
- `TokenCostCalculatorTest.kt`
- `JsonMemoryStoreTest.kt`
- `ConversationSummarizerTest.kt`

## Verification

### Build Status
✅ Project builds successfully: `./gradlew build -x test`
✅ All tests pass: `./gradlew test`

### Key Requirements Met

1. ✅ **Conversation history stored in RAG mode**
   - User messages and LLM answers are saved via `ConversationManager`
   - History persists between queries (JSON or SQLite)

2. ✅ **Chunks NOT stored in history**
   - Only user message and final LLM answer are added to history
   - Retrieved chunks are used internally but not persisted

3. ✅ **Last 5 messages used for context**
   - Configurable via `ragHistoryContextSize`
   - Messages concatenated and embedded together
   - System prompts excluded from context

4. ✅ **Context-aware retrieval**
   - Combined embedding includes conversation context
   - More relevant chunks retrieved based on conversation flow

5. ✅ **Backward compatibility**
   - One-time queries (`/ask`, `/rag <question>`) still work without history
   - Existing `query()` method unchanged

## Usage

### Enable RAG Mode with History

1. **Configure message history** (in `ailearn.config.properties`):
   ```properties
   use.message.history=true
   memory.store.type=json  # or sqlite
   ```

2. **Start application**:
   ```bash
   ./gradlew run
   ```

3. **Enable RAG mode**:
   ```
   /rag
   ```

4. **Ask questions** (all queries now use RAG with history):
   ```
   What is RAG?
   How does it work?  # This query will use context from previous question
   ```

### Configure History Context Size

**Environment variable**:
```bash
export AILEARN_RAG_HISTORY_CONTEXT_SIZE=10
./gradlew run
```

**Config file**:
```properties
rag.history.context.size=10
```

### One-Time Queries (No History)

These commands work independently without affecting conversation history:
```
/ask What is RAG?
/rag How does indexing work?
```

## Architecture

```
User Query (RAG mode enabled)
    ↓
CliFrontend.handleUserRequest()
    ↓
ConversationManager.sendRequestWithRag()
    ↓
1. Add user message to history
2. Extract last 5 messages
    ↓
RagQueryService.queryWithHistory()
    ↓
3. Concatenate messages + question
4. Generate embedding for combined text
5. Search for relevant chunks
6. Generate answer with LLM
    ↓
7. Add only answer to history (NOT chunks)
    ↓
Display answer to user
```

## Testing Recommendations

### Manual Testing

1. **Enable RAG mode and send multiple queries**:
   - Verify history is saved between queries
   - Check history file (`ailearn.history.json` or `ailearn.history.db`)
   - Verify chunks are NOT in saved history

2. **Test context-aware retrieval**:
   - Ask: "What is RAG?"
   - Then ask: "How does it work?" (should understand "it" refers to RAG)
   - Verify retrieved chunks are more relevant than without history

3. **Test one-time queries**:
   - Use `/ask What is RAG?`
   - Verify it doesn't affect conversation history
   - Verify it works independently

4. **Test `/clear` command**:
   - Clear history in RAG mode
   - Verify next query starts fresh without previous context

### Automated Testing

Consider adding integration tests for:
- `ConversationManager.sendRequestWithRag()` with mock RAG service
- `RagQueryService.queryWithHistory()` with test embeddings
- History persistence verification (chunks not stored)

## Benefits

1. **Context-Aware Retrieval**: Embeddings include conversation history, leading to more relevant chunk retrieval
2. **Conversation Continuity**: Users can ask follow-up questions naturally
3. **Clean History**: Only user messages and answers stored, not internal RAG chunks
4. **Configurable**: History context size can be adjusted per use case
5. **Backward Compatible**: Existing functionality unchanged

## Future Enhancements

Potential improvements:
- Add token usage tracking for RAG queries (currently placeholder)
- Implement weighted embeddings (more recent messages have higher weight)
- Add configuration for which message roles to include in context
- Display retrieved chunks info in RAG mode (optional)
- Add metrics for context-aware vs context-free retrieval quality

