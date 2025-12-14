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
│   │   └── TokenCostCalculator.kt
│   ├── config/                      # Configuration management
│   │   ├── AppConfig.kt
│   │   └── ConfigLoader.kt
│   └── utils/                       # Core utilities
│       └── AutoCloseableExtensions.kt
├── api/                             # API implementations
│   ├── provider/
│   │   └── ProviderFactory.kt       # Factory for creating providers
│   └── perplexity/                  # Perplexity API implementation
│       ├── PerplexityModels.kt      # API-specific models
│       ├── PerplexityAdapter.kt     # Adapter between domain and API models
│       └── PerplexityProvider.kt    # Perplexity implementation
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
- **ConversationManager**: Manages conversation state and handles chat requests with automatic summarization
- **ConversationSummarizer**: Handles conversation summarization when token usage exceeds threshold
- **TokenCostCalculator**: Calculates token costs and formats usage information

**Key Design Decisions**:
- No dependencies on external frameworks (Ktor, etc.)
- All models are pure Kotlin data classes
- Business logic is testable without mocking HTTP clients

### API Layer

**Purpose**: Implements LLM provider interfaces for specific APIs.

- **ProviderFactory**: Creates provider instances based on configuration
- **PerplexityProvider**: Perplexity AI API implementation
- **Adapters**: Convert between domain models and API-specific models

**Key Design Decisions**:
- Each provider has its own models (allowing for API-specific differences)
- Adapters handle conversion between domain and API models
- Providers implement `LlmProvider` interface and `AutoCloseable` for resource management
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
- `AILEARN_SUMMARIZATION_TOKEN_THRESHOLD`: Token threshold for triggering summarization (default: 600)
- `AILEARN_SUMMARIZATION_MODEL`: Model to use for summarization (default: "sonar")
- `AILEARN_SUMMARIZATION_MAX_TOKENS`: Max tokens for summarization request (default: 500)
- `AILEARN_SUMMARIZATION_TEMPERATURE`: Temperature for summarization (default: 0.3)
- `AILEARN_SUMMARIZATION_SYSTEM_PROMPT`: System prompt for summarization requests
- `AILEARN_SUMMARIZATION_PROMPT`: Instruction prompt for summarization

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
summarization.token.threshold=600
summarization.model=sonar
summarization.max.tokens=500
summarization.temperature=0.3
summarization.system.prompt=You are a helpful assistant that summarizes conversations concisely.
summarization.prompt=Please provide a brief summary of the conversation so far, capturing the key topics, questions, and answers discussed. Keep it concise and focused on the main points.
```

## SOLID Principles

### Single Responsibility Principle (SRP)
- Each class has a single, well-defined responsibility
- `ConversationManager` manages conversations and triggers summarization when needed
- `ConversationSummarizer` handles conversation summarization logic
- `TokenCostCalculator` calculates costs
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
