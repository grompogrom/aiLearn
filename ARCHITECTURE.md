# Architecture Overview

This document describes the modular, scalable architecture of the aiLearn application.

## Architecture Principles

The application follows **Clean Architecture** principles with clear separation of concerns:

1. **Core Domain Layer**: Business logic and domain models, independent of external frameworks
2. **API Layer**: LLM provider implementations (Perplexity, with interfaces for others)
3. **Frontend Layer**: User interface implementations (CLI, with interfaces for GUI/HTTP API)
4. **Configuration Layer**: Centralized, flexible configuration management

## Directory Structure

```
src/main/kotlin/
├── Main.kt                          # Application entry point
├── core/                            # Core business logic (framework-independent)
│   ├── domain/                      # Domain models
│   │   ├── Message.kt
│   │   ├── MessageRole.kt
│   │   ├── TokenUsage.kt
│   │   ├── ChatRequest.kt
│   │   └── ChatResponse.kt
│   ├── provider/                    # LLM provider interface
│   │   └── LlmProvider.kt
│   ├── conversation/                # Business logic
│   │   ├── ConversationManager.kt
│   │   ├── ConversationSummarizer.kt
│   │   ├── TokenCostCalculator.kt
│   │   ├── ToolCallingHandler.kt    # Handles tool calling loop
│   │   └── ToolRequestParser.kt     # Parses tool requests from LLM
│   ├── mcp/                         # MCP (Model Context Protocol) domain
│   │   └── McpModels.kt             # MCP domain models and service interface
│   ├── memory/                      # Persistent storage for conversation history
│   │   ├── MemoryStore.kt           # Storage interface
│   │   ├── JsonMemoryStore.kt       # JSON file implementation
│   │   ├── SqliteMemoryStore.kt    # SQLite database implementation
│   │   └── MemoryStoreFactory.kt    # Factory for creating stores
│   ├── config/                      # Configuration management
│   │   ├── AppConfig.kt
│   │   └── ConfigLoader.kt
│   └── utils/                       # Core utilities
│       └── AutoCloseableExtensions.kt
├── api/                             # API implementations
│   ├── provider/
│   │   └── ProviderFactory.kt       # Factory for creating providers
│   ├── perplexity/                  # Perplexity API implementation
│   │   ├── PerplexityModels.kt      # API-specific models
│   │   ├── PerplexityAdapter.kt     # Adapter between domain and API models
│   │   └── PerplexityProvider.kt    # Perplexity implementation
│   └── mcp/                         # MCP server integration
│       ├── McpConfig.kt             # MCP server configuration and transport types
│       ├── McpClient.kt             # Common interface for MCP clients
│       ├── McpSseClient.kt          # SSE client for MCP servers
│       ├── McpStreamableHttpClient.kt # StreamableHttp client for MCP servers
│       ├── McpServiceImpl.kt        # MCP service implementation
│       ├── McpSseModels.kt          # MCP SSE protocol models
│       └── McpSseResponseParser.kt  # Parser for MCP responses
└── frontend/                        # Frontend implementations
    ├── Frontend.kt                  # Frontend interface
    └── cli/
        └── CliFrontend.kt           # CLI implementation
```

## Layer Descriptions

### Core Domain Layer

**Purpose**: Contains all business logic and domain models that are independent of external frameworks.

- **Domain Models**: Pure data classes representing core concepts (Message, ChatRequest, ChatResponse, etc.)
- **LlmProvider Interface**: Abstraction for LLM providers, allowing easy swapping of providers
- **ConversationManager**: Manages conversation state and handles chat requests with automatic summarization and tool calling
- **ConversationSummarizer**: Handles conversation summarization when token usage exceeds threshold
- **TokenCostCalculator**: Calculates token costs and formats usage information
- **ToolCallingHandler**: Manages the iterative tool calling loop (LLM → Tool Request → Tool Execution → LLM → Final Answer)
- **ToolRequestParser**: Parses tool requests from LLM responses in various JSON formats
- **McpService Interface**: Abstraction for MCP (Model Context Protocol) server interactions
- **MemoryStore**: Interface for persistent storage of conversation history
- **JsonMemoryStore**: JSON file-based storage implementation
- **SqliteMemoryStore**: SQLite database-based storage implementation

**Key Design Decisions**:
- No dependencies on external frameworks (Ktor, etc.)
- All models are pure Kotlin data classes
- Business logic is testable without mocking HTTP clients

### API Layer

**Purpose**: Implements LLM provider interfaces for specific APIs and MCP server integration.

- **ProviderFactory**: Creates provider instances based on configuration
- **PerplexityProvider**: Perplexity AI API implementation
- **Adapters**: Convert between domain models and API-specific models
- **McpServiceImpl**: Coordinates access to multiple MCP servers, routes tool calls to correct servers
- **McpClient**: Common interface for all MCP client implementations
- **McpSseClient**: SSE-based client for communicating with MCP servers
- **McpStreamableHttpClient**: StreamableHttp-based client for communicating with MCP servers
- **McpConfig**: Configuration for MCP servers (host, port, baseUrl, transportType, timeout)
- **McpTransportType**: Enum for transport types (SSE, STREAMABLE_HTTP)
- **McpSseResponseParser**: Parses MCP server responses

**Key Design Decisions**:
- Each provider has its own models (allowing for API-specific differences)
- Adapters handle conversion between domain and API models
- Providers implement `LlmProvider` interface and `AutoCloseable` for resource management
- MCP integration supports multiple transport types (SSE and StreamableHttp)
- MCP clients implement common `McpClient` interface for interchangeability
- MCP service routes tool calls to servers that actually have the requested tool
- MCP clients support connection reuse for efficiency
- Multiple MCP servers with different transport types can run simultaneously in one session
- Easy to add new providers (OpenAI, Google, etc.) without changing core logic

### Frontend Layer

**Purpose**: User interface implementations.

- **Frontend Interface**: Abstraction for different UI types
- **CliFrontend**: Command-line interface implementation

**Key Design Decisions**:
- Frontend is completely separate from business logic
- Easy to add new frontends (GUI, HTTP API, etc.) without changing core
- Frontend receives `ConversationManager` as dependency

### Configuration Layer

**Purpose**: Centralized, flexible configuration management.

**Configuration Sources (priority order)**:
1. Environment variables (highest priority)
2. Config file (`ailearn.config.properties`)
3. BuildConfig (for API key from `gradle.properties`)
4. Default values (lowest priority)

**Environment Variables**:
- `AILEARN_API_KEY`: API key
- `AILEARN_API_URL`: API endpoint URL
- `AILEARN_MODEL`: Model name
- `AILEARN_MAX_TOKENS`: Maximum tokens per request
- `AILEARN_TEMPERATURE`: Temperature setting
- `AILEARN_SYSTEM_PROMPT`: System prompt
- `AILEARN_DIALOG_END_MARKER`: Dialog end marker
- `AILEARN_PRICE_PER_MILLION_TOKENS`: Price per million tokens
- `AILEARN_REQUEST_TIMEOUT_MILLIS`: Request timeout in milliseconds
- `AILEARN_USE_MESSAGE_HISTORY`: Whether to use message history (true/false)
- `AILEARN_ENABLE_SUMMARIZATION`: Whether to enable conversation summarization (true/false, default: true)
- `AILEARN_SUMMARIZATION_TOKEN_THRESHOLD`: Token threshold for triggering summarization (default: 600)
- `AILEARN_SUMMARIZATION_MODEL`: Model to use for summarization (default: "sonar")
- `AILEARN_SUMMARIZATION_MAX_TOKENS`: Max tokens for summarization request (default: 500)
- `AILEARN_SUMMARIZATION_TEMPERATURE`: Temperature for summarization (default: 0.3)
- `AILEARN_SUMMARIZATION_SYSTEM_PROMPT`: System prompt for summarization requests
- `AILEARN_SUMMARIZATION_PROMPT`: Instruction prompt for summarization
- `AILEARN_MEMORY_STORE_TYPE`: Type of memory store ("json" or "sqlite", default: "json")
- `AILEARN_MEMORY_STORE_PATH`: Optional path for memory store file/database (default: current directory)

**MCP Configuration**:
MCP servers are configured in code via `McpConfig.kt`. Each server can be configured with:
- `id`: Unique identifier for the server
- `host` and `port`: Server address (alternative to baseUrl)
- `baseUrl`: Full base URL for the server (takes precedence over host/port)
- `transportType`: Transport type (SSE or STREAMABLE_HTTP, default: SSE)
- `requestTimeoutMillis`: Request timeout in milliseconds (default: 15000)

**Transport Types**:
- **SSE (Server-Sent Events)**: Traditional MCP transport using Server-Sent Events
- **STREAMABLE_HTTP**: Alternative transport using StreamableHttpClientTransport
- Both transport types can be used simultaneously in the same session

**Config File Format** (`ailearn.config.properties`):
```properties
api.key=your_api_key
api.url=https://api.perplexity.ai/chat/completions
model=sonar
max.tokens=500
temperature=0.6
system.prompt=Answers must be short and succinct
dialog.end.marker=###END###
price.per.million.tokens=1.0
request.timeout.millis=60000
use.message.history=true
enable.summarization=true
summarization.token.threshold=600
summarization.model=sonar
summarization.max.tokens=500
summarization.temperature=0.3
summarization.system.prompt=You are a helpful assistant that summarizes conversations concisely.
summarization.prompt=Please provide a brief summary of the conversation so far, capturing the key topics, questions, and answers discussed. Keep it concise and focused on the main points.
memory.store.type=json
memory.store.path=ailearn.history.json
```

## SOLID Principles

### Single Responsibility Principle (SRP)
- Each class has a single, well-defined responsibility
- `ConversationManager` manages conversations and triggers summarization when needed
- `ConversationSummarizer` handles conversation summarization logic
- `TokenCostCalculator` calculates costs
- `ToolCallingHandler` manages the tool calling loop
- `ToolRequestParser` parses tool requests from LLM responses
- `McpSseClient` handles MCP server communication
- `PerplexityProvider` handles Perplexity API communication
- `CliFrontend` handles CLI interaction

### Open/Closed Principle (OCP)
- Open for extension (new providers, new frontends) without modification
- New providers implement `LlmProvider` interface
- New frontends implement `Frontend` interface

### Liskov Substitution Principle (LSP)
- Any `LlmProvider` implementation can be used interchangeably
- Any `Frontend` implementation can be used interchangeably

### Interface Segregation Principle (ISP)
- Interfaces are focused and minimal
- `LlmProvider` has only one method: `sendRequest`
- `Frontend` has only one method: `start`

### Dependency Inversion Principle (DIP)
- High-level modules (ConversationManager, Frontend) depend on abstractions (LlmProvider, AppConfig)
- Low-level modules (PerplexityProvider) implement abstractions
- Dependencies flow inward: Frontend → Core → API

## RAG System Integration

The application includes a comprehensive RAG (Retrieval-Augmented Generation) system for context-aware responses. For complete RAG documentation, see [RAG_DESCRIPTION.md](RAG_DESCRIPTION.md).

**Key RAG Components:**
- **OllamaClient** - Embedding generation via Ollama API
- **DocumentChunker** - Text splitting with overlap
- **IndexingService** - Pipeline orchestration for indexing
- **RagStorage** - JSON-based index persistence
- **SimilaritySearch** - Cosine similarity for retrieval
- **RagQueryService** - Query pipeline orchestration
- **LlmReranker** - Optional two-stage retrieval with LLM re-ranking

## Extensibility

### Adding a New LLM Provider

1. Create provider-specific models in `api/{provider}/`
2. Create adapter in `api/{provider}/` to convert between domain and API models
3. Implement `LlmProvider` interface
4. Add provider type to `ProviderFactory`
5. Update configuration if needed

Example:
```kotlin
// api/openai/OpenAIProvider.kt
class OpenAIProvider(config: AppConfig) : LlmProvider {
    // Implementation
}

// api/provider/ProviderFactory.kt
enum class ProviderType {
    PERPLEXITY,
    OPENAI  // Add here
}

fun create(type: ProviderType, config: AppConfig): LlmProvider {
    return when (type) {
        ProviderType.OPENAI -> OpenAIProvider(config)  // Add here
        // ...
    }
}
```

### Adding a New Frontend

1. Implement `Frontend` interface
2. Use `ConversationManager` for business logic
3. Update `Main.kt` to use new frontend

Example:
```kotlin
// frontend/http/HttpFrontend.kt
class HttpFrontend(config: AppConfig) : Frontend {
    override suspend fun start(conversationManager: ConversationManager) {
        // HTTP server implementation
    }
}
```

### Adding a New Memory Store

1. Implement `MemoryStore` interface
2. Add new type to `MemoryStoreFactory`
3. Update configuration if needed

Example:
```kotlin
// core/memory/PostgresMemoryStore.kt
class PostgresMemoryStore(config: AppConfig) : MemoryStore {
    // Implementation
}

// core/memory/MemoryStoreFactory.kt
fun create(config: AppConfig): MemoryStore {
    return when (config.memoryStoreType.lowercase()) {
        "postgres" -> PostgresMemoryStore(config)
        // ...
    }
}
```

## MCP Integration and Tool Calling

### Overview

The application integrates with **MCP (Model Context Protocol)** servers to enable tool calling functionality. Since Perplexity API doesn't natively support function calling, a custom tool calling layer was implemented.

### Tool Calling Flow

1. **User sends message** → `ConversationManager.sendRequest()`
2. **Tool calling handler activated** → `ToolCallingHandler.processWithTools()`
3. **System prompt enhanced** → Available tools are described to the LLM
4. **LLM processes request** → May respond with tool requests in JSON format
5. **Tool requests parsed** → `ToolRequestParser` extracts tool calls from LLM response
6. **Tools executed** → `McpService.callTool()` executes tools via MCP servers
7. **Results sent back** → Tool results formatted and sent to LLM as user message
8. **LLM provides final answer** → Loop continues until no more tool requests

### Components

#### ToolCallingHandler
- Manages the iterative tool calling loop
- Enhances system prompt with available tool descriptions
- Executes tools and formats results for LLM consumption
- Prevents infinite loops with max iteration limit (10)

#### ToolRequestParser
- Parses tool requests from LLM responses
- Supports multiple JSON formats:
  - Single object: `{"tool": "tool_name", "arguments": {...}}`
  - Array: `[{"tool": "tool1", "arguments": {...}}]`
  - Markdown code blocks with JSON
  - Inline patterns

#### McpService
- Interface for MCP server interactions
- Methods:
  - `getAvailableTools()`: Retrieves list of available tools
  - `callTool(toolName, arguments)`: Executes a tool with given arguments

#### McpClient
- Common interface for all MCP client implementations
- Defines standard methods: `listTools()` and `callTool()`
- Allows different transport types to be used interchangeably

#### McpSseClient
- SSE-based client for MCP servers
- Implements `McpClient` interface
- Maintains persistent connections for efficiency
- Handles connection lifecycle and error recovery

#### McpStreamableHttpClient
- StreamableHttp-based client for MCP servers
- Implements `McpClient` interface
- Uses StreamableHttpClientTransport from MCP SDK
- Maintains persistent connections for efficiency
- Handles connection lifecycle and error recovery

### Configuration

MCP servers are configured in `api/mcp/McpConfig.kt`. Each server can use either SSE or StreamableHttp transport:

```kotlin
enum class McpTransportType {
    SSE,              // Server-Sent Events transport
    STREAMABLE_HTTP   // Streamable HTTP transport
}

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

**Configuration Options**:
- `id`: Unique identifier for the server
- `host` and `port`: Server address (alternative to baseUrl)
- `baseUrl`: Full base URL for the server (takes precedence over host/port)
- `transportType`: Transport type to use (SSE or STREAMABLE_HTTP, default: SSE)
- `requestTimeoutMillis`: Request timeout in milliseconds (default: 15000)

**Mixed Transport Support**:
- Multiple servers with different transport types can be configured simultaneously
- Tools from all servers are aggregated regardless of transport type
- Tool calls are automatically routed to the server that has the requested tool

### Tool Calling Behavior

- **Automatic**: Tool calling is automatically enabled when MCP service is available and message history is enabled
- **Iterative**: The system continues the loop until LLM provides a final answer (no tool requests)
- **Smart Routing**: Tools are automatically called in the server that has them (not just the first server)
- **Multi-Server Support**: Tools from multiple MCP servers (with different transport types) are aggregated
- **Error Handling**: Tool execution errors are formatted and sent back to LLM for handling
- **Transparent**: Users see the final answer, not intermediate tool requests

### Tool Routing Logic

When a tool is requested:
1. System checks all configured MCP servers to find which ones have the requested tool
2. Tool is called only in servers that have it
3. If tool exists in multiple servers, they are tried in order until one succeeds
4. If tool doesn't exist in any server, a clear error message is returned

## Testing Strategy

The architecture supports easy testing:

- **Unit Tests**: Test business logic (ConversationManager, TokenCostCalculator) with mocked providers
- **Integration Tests**: Test provider implementations with test API keys
- **E2E Tests**: Test full flow with mocked or real providers

## Migration from Old Architecture

The old files (`ApiClient.kt`, `Config.kt`, old `Main.kt`) are preserved for reference but are no longer used. The new architecture:

- Preserves all existing functionality
- Maintains backward compatibility with configuration
- Improves testability and extensibility
- Follows clean architecture principles

## Benefits of New Architecture

1. **Modularity**: Clear separation of concerns
2. **Testability**: Easy to mock dependencies
3. **Extensibility**: Easy to add new providers and frontends
4. **Maintainability**: Small, focused classes
5. **Flexibility**: Configuration from multiple sources
6. **Scalability**: Architecture supports growth
7. **Tool Calling**: Custom tool calling layer enables MCP integration with any LLM provider
8. **MCP Integration**: Standardized protocol for tool and data access
9. **RAG System**: Integrated retrieval-augmented generation with semantic search and LLM re-ranking

## Related Documentation

- [PROJECT_DESCRIPTION.md](PROJECT_DESCRIPTION.md) - Complete project overview and usage guide
- [RAG_DESCRIPTION.md](RAG_DESCRIPTION.md) - RAG system documentation (indexing, querying, re-ranking)
- [README.md](README.md) - Quick start guide (in Russian)
