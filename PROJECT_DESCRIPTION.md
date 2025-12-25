# Project Description

## Overview

**aiLearn** is a Kotlin-based terminal application that provides an interactive chat interface with multiple LLM providers (Perplexity AI by default). The application enables users to have conversational interactions with an AI assistant, with support for message history management, token usage tracking, configurable dialog behavior, **MCP (Model Context Protocol) tool calling integration**, **RAG (Retrieval-Augmented Generation) system**, and **automated reminder checking**.

## Project Purpose

The application serves as a learning tool and interactive AI assistant that:
- Maintains conversation context through message history with persistent storage (JSON/SQLite)
- Tracks token usage and calculates API costs
- Supports both stateful (with history) and stateless (independent) requests
- Provides a clean terminal-based user interface
- Handles API errors gracefully with detailed error reporting
- **Integrates with MCP servers for tool calling functionality**
- **Automatically executes tools requested by the LLM and returns results**
- **Provides RAG capabilities for knowledge base indexing and querying**
- **Supports automated reminder checking via background tasks**
- **Automatically summarizes conversation history when token threshold is exceeded**

## Technology Stack

- **Language**: Kotlin 2.2.10
- **Build Tool**: Gradle with Kotlin DSL
- **HTTP Client**: Ktor 3.3.2 (CIO engine)
- **Serialization**: Kotlinx Serialization (JSON)
- **Concurrency**: Kotlin Coroutines
- **Logging**: Logback (SLF4J) with file-based rolling logs
- **Database**: SQLite 3.44.1.0 (optional, for conversation persistence)
- **Java Version**: JVM 21
- **API**: Perplexity AI Chat Completions API (default provider)
- **MCP SDK**: Model Context Protocol Kotlin SDK 0.8.1
- **Embeddings**: Ollama (for RAG system)

## Project Structure

The project follows Clean Architecture principles. See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architecture documentation.

```
src/main/kotlin/
├── Main.kt                          # Application entry point
├── core/                            # Core business logic
│   ├── domain/                      # Domain models
│   ├── provider/                    # LLM provider interface
│   ├── conversation/                # Business logic
│   │   ├── ConversationManager.kt
│   │   ├── ConversationSummarizer.kt
│   │   ├── TokenCostCalculator.kt
│   │   ├── ToolCallingHandler.kt    # Tool calling loop management
│   │   └── ToolRequestParser.kt     # Parses tool requests from LLM
│   ├── mcp/                         # MCP domain models
│   ├── memory/                      # Persistent storage
│   ├── config/                      # Configuration management
│   └── utils/                       # Core utilities
├── api/                             # API implementations
│   ├── provider/                     # Provider factory
│   ├── perplexity/                  # Perplexity API implementation
│   └── mcp/                         # MCP server integration
│       ├── McpConfig.kt             # MCP server configuration and transport types
│       ├── McpClient.kt             # Common interface for MCP clients
│       ├── McpSseClient.kt          # SSE client for MCP servers
│       ├── McpStreamableHttpClient.kt # StreamableHttp client for MCP servers
│       ├── McpServiceImpl.kt        # MCP service implementation
│       └── McpSseModels.kt
└── frontend/                        # Frontend implementations
    └── cli/                         # CLI frontend
```

## Core Components

### Main.kt
- **Responsibility**: Application entry point and dependency initialization
- **Key Features**:
  - Configuration loading via `ConfigLoader`
  - API key validation
  - MCP client initialization (supports multiple servers with mixed transport types)
  - Ollama client creation for RAG
  - RAG services initialization (IndexingService, RagQueryService, LlmReranker)
  - Memory store creation (JSON or SQLite based on configuration)
  - Conversation manager initialization with history loading
  - Reminder checker creation (disabled by default)
  - CLI frontend initialization and startup
  - Proper resource management with `use {}` blocks
  - Comprehensive logging throughout initialization
- **Architecture**: Follows dependency injection pattern, creating all components and wiring them together

### ConversationManager
- **Responsibility**: Core business logic for conversation management
- **Key Features**:
  - Message history management with persistent storage
  - Automatic conversation summarization when token threshold exceeded
  - Tool calling integration (when MCP service available)
  - Two request modes:
    - `sendRequest()`: Uses message history and tool calling
    - `sendRequestWithoutHistory()`: For background tasks (e.g., reminder checking)
  - Asynchronous history saving (non-blocking)
  - Summarization callbacks for user notification
- **Dependencies**: LlmProvider, AppConfig, MemoryStore, McpService

### Configuration System
- **Responsibility**: Flexible, multi-source configuration management
- **Components**:
  - `AppConfig`: Interface defining all configuration properties
  - `ConfigLoader`: Loads and merges configuration from multiple sources
  - `CompositeConfig`: Implements fallback chain (env vars → config file → BuildConfig → defaults)
  - `DefaultConfig`: Object with all default values
- **Configuration Sources** (priority order):
  1. Environment variables (highest priority)
  2. Config file (`ailearn.config.properties`)
  3. BuildConfig (gradle.properties, build-time injection)
  4. Default values (lowest priority)
- **Key Configuration Areas**:
  - API settings (key, URL, model, tokens, temperature)
  - History and summarization settings
  - Memory store configuration (type, path)
  - MCP settings (timeout)
  - RAG settings (re-ranking, candidate count, model)

### Domain Models

#### Core Domain (`core/domain/`)
- `Message`: Represents a single message with role and content
  - Factory method: `Message.create(MessageRole, String)`
- `MessageRole`: Enum (SYSTEM, USER, ASSISTANT) for type-safe role management
- `ChatRequest`: API request payload with model, messages list, max_tokens, temperature
- `ChatResponse`: Complete API response with content and token usage
- `TokenUsage`: Token usage statistics (prompt_tokens, completion_tokens, total_tokens)

#### MCP Domain (`core/mcp/`)
- `McpToolInfo`: Represents an MCP tool (name, description, inputSchema)
- `McpError`: Sealed class hierarchy for typed MCP errors:
  - `NotConfigured`: MCP not configured
  - `ConnectionFailed`: Network/connection failure
  - `ServerError`: MCP server returned error
  - `InvalidResponse`: Response parsing failure
  - `Timeout`: Request timeout
- `McpResult<T>`: Result wrapper (Success or Error) for MCP operations
- `McpService`: Interface for MCP server interactions

#### RAG Domain (`core/rag/`)
- `RawDocument`: Unprocessed document (path, content)
- `DocumentChunk`: Text chunk with metadata (text, source, chunkIndex)
- `EmbeddedChunk`: Chunk with embedding vector
- `RagIndex`: Complete index (chunks, metadata)
- `RagQueryResult`: Query result (answer, retrieved chunks)
- `RetrievedChunk`: Chunk with relevance scores (text, source, similarity, cosineScore, llmScore)
- `RerankedChunk`: Chunk with both cosine and LLM scores

### Tool Calling Components

#### ToolCallingHandler
- **Responsibility**: Manages iterative tool calling loop
- **Key Features**:
  - Enhances system prompt with tool descriptions
  - Iterative execution (max 10 iterations)
  - Tool result formatting for LLM consumption
  - Smart routing via McpService
- **Flow**: User message → LLM → Tool requests → Tool execution → LLM → Final answer

#### ToolRequestParser
- **Responsibility**: Parses tool requests from LLM responses
- **Supported Formats**:
  - Single object: `{"tool": "name", "arguments": {...}}`
  - Array: `[{"tool": "name", "arguments": {...}}]`
  - Markdown code blocks with JSON
  - Inline patterns: `CALL_TOOL: name({...})`
- **Output**: List of `ToolRequest` objects (toolName, arguments)

## Architecture Patterns

### 1. Sealed Classes for Error Handling
- `ApiException` uses sealed class hierarchy for exhaustive error handling
- Enables pattern matching and type-safe error propagation

### 2. Factory Methods
- `Message.create()` provides convenient object creation
- Encapsulates role enum conversion

### 3. Extension Functions
- String and Int extensions for utility operations
- Keeps utility code organized and discoverable

### 4. Resource Management
- `ApiClient` implements `AutoCloseable`
- Used with `use {}` block in Main.kt for automatic cleanup

### 5. Configuration as Code
- BuildConfig plugin injects secrets at build time
- Config object centralizes all configuration constants

### 6. Message History Pattern
- Optional history management via constructor parameter
- History maintained as mutable list, converted to immutable for requests
- Supports both stateful and stateless conversation modes

## API Integration

### Endpoint
- **URL**: `https://api.perplexity.ai/chat/completions`
- **Method**: POST
- **Headers**:
  - `accept: application/json`
  - `content-type: application/json`
  - `Authorization: Bearer {API_KEY}`

### Request Flow
1. Build `ChatRequest` with messages, model, max_tokens, temperature
2. Serialize to JSON using Kotlinx Serialization
3. Send POST request via Ktor HttpClient
4. Handle response:
   - Success (2xx): Parse `ChatResponse`, extract content
   - Error (non-2xx): Return error message in `ApiResponse`
5. Update message history if enabled
6. Return `ApiResponse` with content and usage

### Error Handling
- Non-2xx status codes: Error message included in response content
- JSON parsing failures: Fallback to raw response body
- Network errors: Propagated as exceptions, caught in Main.kt

## Configuration Management

### Configuration Sources (Priority Order)
1. **Environment Variables** (highest priority)
2. **Config File** (`ailearn.config.properties`)
3. **BuildConfig** (gradle.properties, build-time injection)
4. **Default Values** (lowest priority)

### Key Environment Variables

**API Configuration:**
- `AILEARN_API_KEY` - API key for LLM provider
- `AILEARN_API_URL` - API endpoint URL
- `AILEARN_MODEL` - Model name (default: "sonar")
- `AILEARN_MAX_TOKENS` - Maximum tokens per request (default: 5000)
- `AILEARN_TEMPERATURE` - Temperature setting (default: 0.3)
- `AILEARN_SYSTEM_PROMPT` - System prompt
- `AILEARN_REQUEST_TIMEOUT_MILLIS` - Request timeout (default: 60000)

**History & Summarization:**
- `AILEARN_USE_MESSAGE_HISTORY` - Enable message history (default: true)
- `AILEARN_ENABLE_SUMMARIZATION` - Enable auto-summarization (default: true)
- `AILEARN_SUMMARIZATION_TOKEN_THRESHOLD` - Token threshold for summarization (default: 4000)
- `AILEARN_SUMMARIZATION_MODEL` - Model for summarization (default: "sonar")
- `AILEARN_SUMMARIZATION_MAX_TOKENS` - Max tokens for summary (default: 500)
- `AILEARN_SUMMARIZATION_TEMPERATURE` - Temperature for summarization (default: 0.3)
- `AILEARN_SUMMARIZATION_SYSTEM_PROMPT` - System prompt for summarization
- `AILEARN_SUMMARIZATION_PROMPT` - Instruction prompt for summarization

**Memory Store:**
- `AILEARN_MEMORY_STORE_TYPE` - Store type: "json" or "sqlite" (default: "json")
- `AILEARN_MEMORY_STORE_PATH` - Optional custom path for store file/database

**MCP Configuration:**
- `AILEARN_MCP_REQUEST_TIMEOUT_MILLIS` - MCP request timeout (default: 15000)
- Note: MCP servers are configured in code via `McpConfig.kt`

**RAG Configuration:**
- `AILEARN_RAG_RERANKING` - Enable re-ranking (default: false)
- `AILEARN_RAG_RERANKING_PROVIDER` - Re-ranking provider: "ollama" or "llm" (default: "ollama")
- `AILEARN_RAG_CANDIDATE_COUNT` - Retrieval candidate count (default: 15)
- `AILEARN_RAG_RERANK_MODEL` - Model for Ollama re-ranking (default: "qwen2.5")

### Config File Format
Create `ailearn.config.properties` in project root:
```properties
api.key=your_api_key
api.url=https://api.perplexity.ai/chat/completions
model=sonar
max.tokens=5000
temperature=0.3
use.message.history=true
enable.summarization=true
summarization.token.threshold=4000
memory.store.type=json
memory.store.path=ailearn.history.json
rag.reranking=false
rag.reranking.provider=ollama
rag.candidate.count=15
rag.rerank.model=qwen2.5
```

### Build-time Configuration
- API key can be stored in `gradle.properties` (excluded from git)
- Injected via BuildConfig plugin during build
- Property name: `perplexityApiKey`

### Configuration Architecture
- `AppConfig` - Interface for configuration values
- `ConfigLoader` - Loads and merges configuration from all sources
- `CompositeConfig` - Implements fallback chain (primary → fallback)
- `DefaultConfig` - Object with all default constants

## Code Style & Conventions

### Naming
- Classes: PascalCase (`ApiClient`, `ChatRequest`)
- Functions: camelCase (`sendRequest`, `extractContentFromResponse`)
- Constants: UPPER_SNAKE_CASE (`API_URL`, `MAX_TOKENS`)
- Private functions: camelCase with descriptive names

### Kotlin Idioms
- Data classes for models
- Sealed classes for error types
- Extension functions for utilities
- Companion objects for factory methods
- Null safety with `?` and `?.let {}`
- String templates for formatting
- `use {}` blocks for resource management

### Error Handling
- Try-catch in Main.kt for user-facing errors
- Sealed classes for API error types
- Graceful degradation (fallback to raw response on parse failure)

## Dependencies

### Runtime
- `io.ktor:ktor-client-core:3.3.2` - HTTP client core
- `io.ktor:ktor-client-cio:3.3.2` - CIO engine for async I/O
- `io.ktor:ktor-client-content-negotiation:3.3.2` - Content negotiation
- `io.ktor:ktor-serialization-kotlinx-json:3.3.2` - JSON serialization
- `io.modelcontextprotocol:kotlin-sdk-client:0.8.1` - MCP SDK (supports both SSE and StreamableHttp transports)
- `org.slf4j:slf4j-api:2.0.13` - Logging facade
- `ch.qos.logback:logback-classic:1.5.8` - Logging implementation with rolling file appender
- `org.xerial:sqlite-jdbc:3.44.1.0` - SQLite database for conversation persistence

### Test
- `kotlin-test` - Kotlin testing framework
- `io.ktor:ktor-client-mock:3.3.2` - HTTP client mocking
- `io.mockk:mockk:1.13.10` - Mocking framework for Kotlin

### Build
- `com.github.gmazzo.buildconfig:5.5.1` - BuildConfig plugin for build-time value injection

## Current Features

### Core Features
1. ✅ Interactive terminal CLI
2. ✅ Message history management (optional)
3. ✅ Independent request mode (no history)
4. ✅ Token usage tracking and cost calculation
5. ✅ Dialog end marker detection
6. ✅ Error handling with detailed messages and typed error hierarchy
7. ✅ HTTP timeout configuration
8. ✅ JSON serialization/deserialization
9. ✅ System prompt configuration
10. ✅ Temperature and max_tokens per-request configuration
11. ✅ Flexible configuration (environment variables, config file, BuildConfig)

### Tool Calling & MCP
12. ✅ **MCP (Model Context Protocol) integration**
13. ✅ **Automatic tool calling with iterative execution loop**
14. ✅ **Tool request parsing from LLM responses (multiple formats)**
15. ✅ **Multiple MCP server support with mixed transport types**
16. ✅ **SSE and StreamableHttp transport support**
17. ✅ **Smart tool routing (tools called in correct server)**
18. ✅ **McpClientsManager for lifecycle management**

### Memory & Summarization
19. ✅ **Persistent conversation history (JSON/SQLite)**
20. ✅ **Automatic conversation summarization when token threshold exceeded**
21. ✅ **Configurable summarization (threshold, model, prompts)**
22. ✅ **Background asynchronous history saving**

### RAG System
23. ✅ **Document indexing with semantic embeddings (Ollama)**
24. ✅ **Cosine similarity search for chunk retrieval**
25. ✅ **Optional LLM re-ranking (Ollama or LLM provider)**
26. ✅ **Two re-ranking implementations: OllamaReranker and ProviderReranker**
27. ✅ **Source attribution with relevance scores**
28. ✅ **CLI commands: /index and /ask**

### Advanced Features
29. ✅ **Automated reminder checking (background coroutine, toggle via /reminder)**
30. ✅ **Structured logging with Logback (file-based with rolling policy)**
31. ✅ **Comprehensive test coverage (unit, integration)**

## Design Decisions

### Why Optional History?
- Allows comparison of responses with/without context
- Useful for testing different conversation strategies
- Enables stateless request mode for independent queries

### Why Two Request Methods?
- `sendRequest()`: For conversational flow with context
- `sendIndependentRequest()`: For isolated queries without history contamination

### Why ApiResponse Wrapper?
- Separates internal representation from API response
- Combines content and usage in single return value
- Simplifies error handling (content can be error message)

### Why Sealed Classes for Errors?
- Type-safe error handling
- Exhaustive when expressions
- Clear error hierarchy
- Better IDE support

## MCP Integration and Tool Calling

### Overview

The application integrates with **MCP (Model Context Protocol)** servers to enable tool calling functionality. Since Perplexity API doesn't natively support function calling, a custom tool calling layer was implemented.

### How It Works

1. **User sends message** → The message is processed by `ConversationManager`
2. **Tool calling activated** → If MCP service is available, `ToolCallingHandler` manages the flow
3. **System prompt enhanced** → Available tools from MCP servers are described to the LLM
4. **LLM responds** → May include tool requests in JSON format: `{"tool": "tool_name", "arguments": {...}}`
5. **Tools executed** → `ToolRequestParser` extracts tool calls, `McpService` executes them
6. **Results processed** → Tool results are sent back to LLM as a user message
7. **Final answer** → LLM processes tool results and provides the final answer

### Tool Request Formats

The parser supports multiple formats:
- Single object: `{"tool": "tool_name", "arguments": {"param": "value"}}`
- Array: `[{"tool": "tool1", "arguments": {...}}]`
- Markdown code blocks: `` ```json {"tool": "tool_name", "arguments": {...}} ``` ``
- Inline patterns: `CALL_TOOL: tool_name({"arg": "value"})`

### Configuration

MCP servers are configured in `api/mcp/McpConfig.kt`. You can configure multiple servers with different transport types:

```kotlin
val servers: List<McpServerConfig> = listOf(
    McpServerConfig(
        id = "sse-server",
        baseUrl = "http://127.0.0.1:3002/sse",
        transportType = McpTransportType.SSE,
        requestTimeoutMillis = 15_000L
    ),
    McpServerConfig(
        id = "http-server",
        baseUrl = "http://127.0.0.1:8000/mcp",
        transportType = McpTransportType.STREAMABLE_HTTP,
        requestTimeoutMillis = 15_000L
    )
)
```

**Transport Types**:
- `SSE`: Server-Sent Events transport (default, for backward compatibility)
- `STREAMABLE_HTTP`: Streamable HTTP transport

**Key Features**:
- Multiple servers can run simultaneously with different transport types
- Tools from all servers are automatically aggregated
- Tool calls are routed to the server that has the requested tool
- If a tool exists in multiple servers, they are tried in order until one succeeds
- `McpClientsManager` handles lifecycle management of all MCP clients
- Smart error handling: if some servers fail, others continue to work

## Reminder System

The application includes an automated reminder checking system that runs in the background.

**Key Features:**
- Periodic reminder checking (every 60 seconds) via MCP tool (`reminder.list`)
- Background coroutine execution (non-blocking)
- Results processed by LLM and displayed to user
- Does not save reminder checks to conversation history
- Toggle on/off via `/reminder` command (disabled by default)

**Components:**
- `ReminderChecker` - Background service for periodic reminder checking
- Configurable output callback for displaying results
- Uses `ConversationManager.sendRequestWithoutHistory()` to avoid polluting conversation history

**CLI Command:**
```bash
# Toggle reminder checking on/off
/reminder
```

## Logging System

The application uses **Logback** for structured logging with file-based output (no console logging to avoid interfering with CLI interaction).

**Configuration:**
- Log file: `ailearn.log` in current directory
- Rolling policy: Size-based (100MB per file) and time-based (daily)
- Retention: 30 days, max 1GB total
- Format: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`
- Log levels:
  - Application code: DEBUG
  - External libraries (Ktor, MCP SDK): WARN
- Configuration file: `src/main/resources/logback.xml`

## RAG System

The application includes a comprehensive RAG (Retrieval-Augmented Generation) system that enables context-aware AI responses by indexing and searching through your knowledge base.

**Key Features:**
- Document indexing with semantic embeddings (via Ollama `mxbai-embed-large` model)
- Cosine similarity search for relevant chunks
- Optional LLM re-ranking for improved relevance (two implementations: OllamaReranker and ProviderReranker)
- Source attribution with relevance scores
- Support for multiple file types (Markdown, text)
- Configurable chunk size and overlap
- Progress callbacks during indexing

**Components:**
- `OllamaClient` - Embedding generation via Ollama API
- `DocumentChunker` - Text splitting with configurable overlap
- `IndexingService` - Pipeline orchestration for indexing
- `RagStorage` - JSON-based index persistence
- `SimilaritySearch` - Cosine similarity for retrieval
- `RagQueryService` - Query pipeline orchestration
- `LlmReranker` - Interface with two implementations:
  - `OllamaReranker` - Uses Ollama (default: `qwen2.5` model) for cost-effective local re-ranking
  - `ProviderReranker` - Uses LLM provider (e.g., Perplexity) for higher-quality re-ranking

**Configuration (Environment Variables):**
- `AILEARN_RAG_RERANKING` - Enable/disable re-ranking (default: false)
- `AILEARN_RAG_RERANKING_PROVIDER` - Re-ranking provider: "ollama" or "llm" (default: "ollama")
- `AILEARN_RAG_CANDIDATE_COUNT` - Number of candidates to retrieve (default: 15)
- `AILEARN_RAG_RERANK_MODEL` - Model for Ollama re-ranking (default: "qwen2.5")

**CLI Commands:**
```bash
# Build index from documents in dataForRag/raw/
/index

# Enable RAG mode (toggle) - all queries will use RAG
/rag

# Now all regular queries use RAG automatically
What is RAG?
How does indexing work?

# Disable RAG mode (toggle again)
/rag

# One-time RAG queries (without enabling mode)
/ask What is RAG?
/rag What is RAG?
```

For complete RAG documentation, see [RAG_DESCRIPTION.md](RAG_DESCRIPTION.md).

## CLI Commands Reference

The application provides several built-in commands for different functionalities:

### Conversation Management
- **`exit`** or **`quit`** - Exit the application gracefully
- **`/clear`** or **`/clearhistory`** - Clear conversation history and reset to system prompt

### MCP Tools
- **`/mcp`** - List all available MCP tools from configured servers
  - Shows tool names, descriptions, and input schemas
  - Displays tools from all configured servers (both SSE and StreamableHttp)

### Reminder System
- **`/reminder`** - Toggle automated reminder checking on/off
  - When enabled: checks reminders every 60 seconds via `reminder.list` MCP tool
  - Results are processed by LLM and displayed
  - Does not save to conversation history
  - Disabled by default

### RAG System
- **`/index`** - Build RAG index from documents in `dataForRag/raw/`
  - Processes all Markdown files in the directory
  - Generates embeddings using Ollama (`mxbai-embed-large` model)
  - Saves index to `dataForRag/indexed/index.json`
  - Shows progress during indexing

- **`/rag`** - Toggle RAG mode on/off for all queries
  - When enabled: all regular user queries automatically use RAG system
  - When disabled: normal conversation flow without RAG
  - Disabled by default
  - Similar behavior to `/reminder` toggle command

- **`/ask <question>`** or **`/rag <question>`** - One-time RAG query
  - Performs a single RAG query regardless of RAG mode state
  - Retrieves relevant chunks using cosine similarity
  - Optionally re-ranks results using LLM (if enabled)
  - Generates answer using LLM with retrieved context
  - Displays source attribution and relevance scores

## Potential Improvements & Extension Points

### Features
- [x] ~~Logging framework integration~~ (Implemented: Logback with rolling file appender)
- [x] ~~Unit tests~~ (Implemented: Comprehensive test coverage)
- [x] ~~Integration tests~~ (Implemented: Memory stores, MCP, configuration)
- [x] ~~Configuration file support~~ (Implemented: Properties file + env vars + BuildConfig)
- [ ] Retry logic with exponential backoff
- [ ] Request rate limiting
- [ ] Response streaming support
- [ ] Multiple model support with model selection UI
- [ ] Conversation export/import (to/from different formats)
- [ ] Command-line argument parsing (for batch mode)
- [ ] Interactive mode vs batch mode
- [ ] Response formatting (markdown rendering, HTML export)
- [ ] Search results display (from Perplexity API)
- [ ] Video results display (from Perplexity API)
- [ ] MCP server discovery (automatic detection)
- [ ] Tool result caching (to avoid redundant calls)
- [ ] GUI frontend implementation
- [ ] HTTP API frontend implementation
- [ ] Additional LLM providers (OpenAI, Anthropic, Google)
- [ ] PostgreSQL memory store implementation
- [ ] RAG support for more file types (PDF, DOCX, etc.)

### Refactoring Opportunities
- [x] ~~Extract CLI logic to separate class~~ (Implemented: CliFrontend)
- [x] ~~Separate concerns (UI, business logic, API)~~ (Implemented: Clean Architecture)
- [x] ~~Add logging throughout application~~ (Implemented: Logback)
- [x] ~~Add code documentation~~ (Implemented: KDoc comments)
- [ ] Add dependency injection framework (e.g., Koin)
- [ ] Create repository pattern for API calls
- [ ] Add validation layer for requests
- [ ] Create response parser abstraction
- [ ] Add metrics/telemetry (Prometheus, Grafana)
- [ ] Implement caching layer (Redis, in-memory)

### Code Quality
- [x] ~~Add comprehensive unit tests~~ (Implemented: Core, conversation, memory, MCP)
- [x] ~~Add integration tests~~ (Implemented: Memory stores, configuration)
- [ ] Add E2E tests (full conversation flow)
- [ ] Add error recovery strategies (automatic retry)
- [ ] Improve error messages (localization, i18n)
- [ ] Add input validation (sanitization, length limits)
- [ ] Add request validation (schema validation)
- [ ] Add performance benchmarks
- [ ] Add code coverage reporting
- [ ] Add static code analysis (detekt, ktlint)

## Testing Strategy

### Current State
The project has comprehensive test coverage with unit and integration tests.

### Implemented Tests

1. **Core Configuration Tests** (`ConfigLoaderTest.kt`):
   - Configuration loading from multiple sources
   - Priority order validation
   - Environment variable parsing

2. **Conversation Tests**:
   - `ConversationManagerSummarizationTest.kt` - Summarization trigger logic
   - `ConversationSummarizerTest.kt` - Summary generation
   - `TokenCostCalculatorTest.kt` - Cost calculation accuracy
   - `ToolRequestParserTest.kt` - Tool request parsing from various formats

3. **Memory Tests**:
   - `JsonMemoryStoreTest.kt` - JSON persistence operations
   - `SqliteMemoryStoreTest.kt` - SQLite persistence operations
   - `MemoryStoreFactoryTest.kt` - Factory creation logic

4. **MCP Tests**:
   - `McpSseClientTest.kt` - SSE client functionality
   - `McpSseResponseParserTest.kt` - Response parsing

5. **Domain Tests**:
   - `MessageTest.kt` - Message model validation

### Test Infrastructure
- **Frameworks**: Kotlin Test, JUnit Platform
- **Mocking**: MockK for Kotlin-specific mocking
- **HTTP Mocking**: Ktor Client Mock for API testing
- **Test Location**: `src/test/kotlin/`

## Security Considerations

1. **API Key Management**:
   - Stored in `gradle.properties` (git-ignored)
   - Injected at build time via BuildConfig
   - Never committed to repository

2. **Recommendations**:
   - Consider environment variables for production
   - Use secret management tools for CI/CD
   - Rotate API keys regularly
   - Monitor API usage for anomalies

## Performance Considerations

1. **HTTP Client**:
   - Single client instance per application lifecycle
   - Connection pooling via CIO engine
   - 60-second timeout prevents hanging requests

2. **Memory**:
   - Message history grows with conversation length
   - Consider history size limits for long conversations
   - Current implementation: unlimited history

3. **Network**:
   - Synchronous request handling (blocking)
   - Consider async UI updates for better UX

## Usage Patterns

### Conversational Mode (with history)
```kotlin
ApiClient(useHistory = true).use { client ->
    client.sendRequest("Hello")
    client.sendRequest("Tell me more") // Has context from previous message
}
```

### Independent Mode (no history)
```kotlin
ApiClient(useHistory = false).use { client ->
    client.sendRequest("Question 1") // Only system prompt + question
    client.sendRequest("Question 2") // Only system prompt + question (no context)
}
```

### Mixed Mode
```kotlin
ApiClient(useHistory = true).use { client ->
    client.sendRequest("Start conversation") // Uses history
    client.sendIndependentRequest("Isolated question") // No history
    client.sendRequest("Continue conversation") // History continues from first request
}
```

## Related Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - Detailed architecture documentation
- [RAG_DESCRIPTION.md](RAG_DESCRIPTION.md) - Complete RAG system documentation
- [README.md](README.md) - Quick start guide (in Russian)

## Notes for AI Assistants

When creating new features or refactoring:

1. **Maintain Type Safety**: Use sealed classes, enums, and data classes
2. **Follow Kotlin Conventions**: Extension functions, null safety, idiomatic patterns
3. **Preserve History Behavior**: Respect `useHistory` flag in ApiClient
4. **Error Handling**: Use ApiException hierarchy, provide user-friendly messages
5. **Configuration**: Add new constants to Config.kt object
6. **Resource Management**: Ensure AutoCloseable implementations where needed
7. **Testing**: Consider testability when designing new features
8. **Documentation**: Add KDoc comments for public APIs
9. **Backward Compatibility**: Consider impact on existing usage patterns
10. **API Integration**: Follow existing request/response patterns

## Build Configuration

- **Gradle Version**: Managed via wrapper
- **Kotlin Version**: 2.2.10
- **Java Toolchain**: 21
- **Main Class**: `MainKt`
- **Standard Input**: Enabled for run task (terminal interaction)

## File Naming Conventions

- Kotlin files: PascalCase for classes (`ApiClient.kt`)
- Model files: Descriptive names (`ChatRequest.kt`, `ChatResponse.kt`)
- Utility files: Descriptive names (`StringExtensions.kt`)
- Package structure: Follows domain organization (`models/`, `utils/`)
