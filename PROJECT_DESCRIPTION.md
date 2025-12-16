# Project Description

## Overview

**aiLearn** is a Kotlin-based terminal application that provides an interactive chat interface with Perplexity AI API. The application enables users to have conversational interactions with an AI assistant, with support for message history management, token usage tracking, configurable dialog behavior, and **MCP (Model Context Protocol) tool calling integration**.

## Project Purpose

The application serves as a learning tool and interactive AI assistant that:
- Maintains conversation context through message history
- Tracks token usage and calculates API costs
- Supports both stateful (with history) and stateless (independent) requests
- Provides a clean terminal-based user interface
- Handles API errors gracefully with detailed error reporting
- **Integrates with MCP servers for tool calling functionality**
- **Automatically executes tools requested by the LLM and returns results**

## Technology Stack

- **Language**: Kotlin 2.2.10
- **Build Tool**: Gradle with Kotlin DSL
- **HTTP Client**: Ktor 3.3.2 (CIO engine)
- **Serialization**: Kotlinx Serialization (JSON)
- **Concurrency**: Kotlin Coroutines
- **Logging**: SLF4J Simple
- **Java Version**: JVM 21
- **API**: Perplexity AI Chat Completions API
- **MCP SDK**: Model Context Protocol Kotlin SDK 0.8.1

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
│       ├── McpConfig.kt
│       ├── McpSseClient.kt
│       ├── McpServiceImpl.kt
│       └── McpSseModels.kt
└── frontend/                        # Frontend implementations
    └── cli/                         # CLI frontend
```

## Core Components

### Main.kt
- **Responsibility**: Application entry point and CLI interaction loop
- **Key Features**:
  - Interactive terminal loop with user input handling
  - Exit command detection (`exit`, `quit`)
  - Token usage display with cost calculation
  - Dialog end marker detection (`###END###`)
  - Error handling with user-friendly messages
- **Configuration**: `USE_MESSAGE_HISTORY` flag controls history behavior

### ApiClient.kt
- **Responsibility**: HTTP client wrapper and conversation state management
- **Key Features**:
  - Message history management (optional, configurable)
  - Two request modes:
    - `sendRequest()`: Uses message history if enabled
    - `sendIndependentRequest()`: Always sends fresh request without history
  - Automatic history updates after successful requests
  - HTTP timeout configuration (60 seconds)
  - JSON serialization/deserialization
  - Error response handling
- **Implements**: `AutoCloseable` for resource management
- **Dependencies**: Ktor HttpClient, Config

### Config.kt
- **Responsibility**: Centralized configuration management
- **Key Constants**:
  - `API_URL`: Perplexity API endpoint
  - `API_KEY`: Retrieved from BuildConfig (gradle.properties)
  - `MODEL`: AI model name ("sonar")
  - `MAX_TOKENS`: Maximum tokens per request (500)
  - `TEMPERATURE`: AI temperature setting (0.6)
  - `SYSTEM_PROMPT`: System message for AI behavior
  - `DIALOG_END_MARKER`: Marker string for dialog termination
  - `PRICE_MILLION_TOKENS`: Cost calculation constant
- **Build Integration**: Uses BuildConfig plugin to inject API key from gradle.properties

### Models

#### ChatRequest.kt
- `Message`: Represents a single message with role, content, and disable_search flag
  - Factory method: `Message.create(MessageRole, String, Boolean)`
- `ChatRequest`: API request payload with model, messages list, max_tokens, temperature

#### ChatResponse.kt
- `Usage`: Token usage statistics (prompt_tokens, completion_tokens, total_tokens, etc.)
- `ChoiceMessage`: Message content and role from API response
- `Choice`: Response choice with index, finish_reason, and message
- `ChatResponse`: Complete API response with id, model, usage, choices, search_results, videos
- `SearchResult`: Search result metadata (title, url, date)
- `Video`: Video metadata (url, thumbnails, duration)

#### ApiResponse.kt
- Internal wrapper combining content string and Usage object
- Used to pass both response content and token usage information

#### MessageRole.kt
- Enum with values: SYSTEM, USER, ASSISTANT
- Provides type-safe role management

### ApiException.kt
- **Sealed class hierarchy** for typed error handling:
  - `InvalidJsonResponse`: JSON parsing failures with retry information
  - `EmptyResponse`: Responses without content
  - `RequestFailed`: General request failures
- **Purpose**: Type-safe error handling with detailed error messages

### StringExtensions.kt
- `String.cleanJsonContent()`: Removes JSON code block markers
- `Int.getRetryWord()`: Russian pluralization for retry count

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

### Build-time Configuration
- API key stored in `gradle.properties` (excluded from git)
- Injected via BuildConfig plugin during build
- Property name: `perplexityApiKey`

### Runtime Configuration
- All runtime constants in `Config.kt` object
- System prompt configurable for different use cases
- Temperature, max_tokens, model can be adjusted per request

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
- `io.ktor:ktor-client-core:3.3.2`
- `io.ktor:ktor-client-cio:3.3.2`
- `io.ktor:ktor-client-content-negotiation:3.3.2`
- `io.ktor:ktor-serialization-kotlinx-json:3.3.2`
- `io.ktor:ktor-client-sse:3.3.2` - SSE support for MCP
- `io.modelcontextprotocol:kotlin-sdk-client:0.8.1` - MCP SDK
- `org.slf4j:slf4j-simple:2.0.13`

### Build
- `com.github.gmazzo.buildconfig:5.5.1` (BuildConfig plugin)

## Current Features

1. ✅ Interactive terminal CLI
2. ✅ Message history management (optional)
3. ✅ Independent request mode (no history)
4. ✅ Token usage tracking and cost calculation
5. ✅ Dialog end marker detection
6. ✅ Error handling with detailed messages
7. ✅ HTTP timeout configuration
8. ✅ JSON serialization/deserialization
9. ✅ System prompt configuration
10. ✅ Temperature and max_tokens per-request configuration
11. ✅ **MCP (Model Context Protocol) integration**
12. ✅ **Automatic tool calling with iterative execution loop**
13. ✅ **Tool request parsing from LLM responses**
14. ✅ **Multiple MCP server support**
15. ✅ **Persistent conversation history (JSON/SQLite)**
16. ✅ **Automatic conversation summarization**

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

MCP servers are configured in `api/mcp/McpConfig.kt`:

```kotlin
val servers: List<McpServerConfig> = listOf(
    McpServerConfig(
        id = "default",
        baseUrl = "http://127.0.0.1:3002/sse",
        requestTimeoutMillis = 15_000L
    )
)
```

## Potential Improvements & Extension Points

### Features
- [ ] Retry logic with exponential backoff
- [ ] Request rate limiting
- [ ] Response streaming support
- [ ] Multiple model support with model selection
- [ ] Conversation export/import
- [ ] Configuration file support (YAML/JSON)
- [ ] Logging framework integration
- [ ] Unit tests
- [ ] Integration tests
- [ ] Command-line argument parsing
- [ ] Interactive mode vs batch mode
- [ ] Response formatting (markdown, HTML)
- [ ] Search results display
- [ ] Video results display
- [ ] MCP server discovery
- [ ] Tool result caching

### Refactoring Opportunities
- [ ] Extract CLI logic to separate class
- [ ] Create repository pattern for API calls
- [ ] Add dependency injection
- [ ] Separate concerns (UI, business logic, API)
- [ ] Add validation layer for requests
- [ ] Create response parser abstraction
- [ ] Add metrics/telemetry
- [ ] Implement caching layer

### Code Quality
- [ ] Add comprehensive unit tests
- [ ] Add integration tests for API client
- [ ] Add error recovery strategies
- [ ] Improve error messages (localization)
- [ ] Add logging throughout application
- [ ] Add code documentation (KDoc)
- [ ] Add input validation
- [ ] Add request validation

## Testing Strategy

### Current State
- No tests currently implemented
- Manual testing via terminal interaction

### Recommended Tests
1. **Unit Tests**:
   - ApiClient request building
   - Message history management
   - Error parsing
   - Token cost calculation
   - String extensions

2. **Integration Tests**:
   - API request/response flow
   - Error handling scenarios
   - History management

3. **E2E Tests**:
   - Full conversation flow
   - Exit command handling
   - Dialog end marker detection

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
