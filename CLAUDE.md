# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

aiLearn is a Kotlin-based terminal application that provides an interactive chat interface with multiple LLM providers (Perplexity AI by default). The application enables users to have conversational interactions with an AI assistant, with support for message history management, token usage tracking, configurable dialog behavior, **MCP (Model Context Protocol) tool calling integration**, **RAG (Retrieval-Augmented Generation) system**, and **automated reminder checking**.

The project follows Clean Architecture principles with clear separation of concerns into three main layers:
- **Core Domain Layer**: Business logic and domain models, independent of external frameworks
- **API Layer**: LLM provider implementations and MCP server integrations
- **Frontend Layer**: User interface implementations (currently CLI, extensible to GUI/HTTP API)

## Key Features

1. **Interactive Terminal CLI**: Command-line interface for conversational AI interactions
2. **Message History Management**: Optional persistent conversation history (JSON/SQLite)
3. **Token Usage Tracking**: Automatic calculation of token usage and API costs
4. **MCP Integration**: Model Context Protocol support for tool calling with mixed transport types (SSE and StreamableHttp)
5. **RAG System**: Retrieval-Augmented Generation for knowledge base indexing and querying
6. **Automatic Summarization**: Conversation history compression when token thresholds are exceeded
7. **Configurable Architecture**: Multi-source configuration (environment variables, properties file, BuildConfig)
8. **Reminder System**: Automated background task for periodic reminder checking

## Development Commands

### Build Commands
```bash
# Build the entire project
./gradlew build

# Compile only the main classes
./gradlew classes

# Create a JAR file
./gradlew jar
```

### Run Commands
```bash
# Run the application
./gradlew run

# Or run the compiled JAR directly
java -jar build/libs/aiLearn-1.0-SNAPSHOT.jar
```

### Test Commands
```bash
# Run the entire test suite
./gradlew test

# Run a specific test
./gradlew test --tests "*ConfigLoaderTest*"

# Run tests matching a pattern
./gradlew test --tests "core.config.*"

# Generate test reports
./gradlew test
open build/reports/tests/test/index.html
```

### Clean Commands
```bash
# Clean the build directory
./gradlew clean

# Clean a specific task output
./gradlew cleanJar
```

### Distribution Commands
```bash
# Create distribution archives (tar and zip)
./gradlew distZip
./gradlew distTar

# Install distribution locally
./gradlew installDist
```

## Architecture Structure

### Core Components
- `Main.kt`: Application entry point
- `core/`: Core business logic (domain models, conversation management, configuration)
- `api/`: API implementations (Perplexity, MCP integration, Ollama for RAG)
- `frontend/`: User interface implementations (currently CLI only)

### Domain Models
- `Message`: Represents a single message with role and content
- `ChatRequest`: API request payload with model, messages, and parameters
- `ChatResponse`: Complete API response with content and token usage
- `TokenUsage`: Token usage statistics

### Configuration System
The application uses a multi-source configuration system with the following priority order:
1. Environment variables (highest priority)
2. Config file (`ailearn.config.properties`)
3. BuildConfig (from `gradle.properties`)
4. Default values (lowest priority)

### MCP (Model Context Protocol) Integration
The application supports multiple MCP servers with mixed transport types (SSE and StreamableHttp). Tool calling is handled through an iterative process:
1. User message → LLM processes request
2. LLM may respond with tool requests in JSON format
3. `ToolRequestParser` extracts tool calls from LLM response
4. `McpService` executes tools via appropriate MCP servers
5. Results are formatted and sent back to LLM as user message
6. LLM provides final answer to user

### RAG System
The RAG (Retrieval-Augmented Generation) system includes:
- Document indexing with Ollama embeddings
- Cosine similarity search for chunk retrieval
- Optional LLM re-ranking (Ollama or provider-based)
- Source attribution with relevance scores
- CLI commands: `/index`, `/ask`, `/rag`

## CLI Commands

- `exit` or `quit`: Exit the application
- `/clear` or `/clearhistory`: Clear conversation history
- `/mcp`: List available MCP tools from all configured servers
- `/reminder`: Toggle automated reminder checking
- `/index`: Build RAG index from documents in `dataForRag/raw/`
- `/ask <question>` or `/rag <question>`: Query the RAG knowledge base
- `/rag`: Toggle RAG mode (enables RAG for all queries)

## Configuration Options

### Environment Variables
**API Configuration:**
- `AILEARN_API_KEY`: API key for LLM provider
- `AILEARN_API_URL`: API endpoint URL
- `AILEARN_MODEL`: Model name (default: "sonar")
- `AILEARN_MAX_TOKENS`: Maximum tokens per request (default: 5000)
- `AILEARN_TEMPERATURE`: Temperature setting (default: 0.3)

**History & Summarization:**
- `AILEARN_USE_MESSAGE_HISTORY`: Enable message history (default: true)
- `AILEARN_ENABLE_SUMMARIZATION`: Enable auto-summarization (default: true)
- `AILEARN_SUMMARIZATION_TOKEN_THRESHOLD`: Token threshold for summarization (default: 4000)

**Memory Store:**
- `AILEARN_MEMORY_STORE_TYPE`: Store type: "json" or "sqlite" (default: "json")
- `AILEARN_MEMORY_STORE_PATH`: Custom path for store file/database

**RAG Configuration:**
- `AILEARN_RAG_RERANKING`: Enable re-ranking (default: false)
- `AILEARN_RAG_RERANKING_PROVIDER`: Re-ranking provider: "ollama" or "llm" (default: "ollama")
- `AILEARN_RAG_CANDIDATE_COUNT`: Retrieval candidate count (default: 15)
- `AILEARN_RAG_RERANK_MODEL`: Model for Ollama re-ranking (default: "qwen2.5")

## Testing Strategy

The project has comprehensive test coverage with unit and integration tests:
- Configuration loading tests (`ConfigLoaderTest.kt`)
- Conversation management tests (`ConversationManagerSummarizationTest.kt`)
- Memory store tests (`JsonMemoryStoreTest.kt`, `SqliteMemoryStoreTest.kt`)
- MCP integration tests (`McpSseResponseParserTest.kt`)
- Tool parsing tests (`ToolRequestParserTest.kt`)

## Development Guidelines

When working on this codebase:
1. Maintain type safety using sealed classes, enums, and data classes
2. Follow Kotlin conventions (extension functions, null safety, idiomatic patterns)
3. Preserve history behavior and respect the `useHistory` flag
4. Use the ApiException hierarchy for error handling
5. Ensure proper resource management with AutoCloseable implementations
6. Add KDoc comments for public APIs
7. Consider testability when designing new features
8. Follow the existing request/response patterns for API integration

## File Structure

```
aiLearn/
├── src/main/kotlin/
│   ├── Main.kt                          # Application entry point
│   ├── core/                            # Core business logic
│   │   ├── domain/                      # Domain models
│   │   ├── provider/                    # LLM provider interface
│   │   ├── conversation/                # Business logic
│   │   ├── mcp/                         # MCP domain models
│   │   ├── memory/                      # Persistent storage
│   │   ├── config/                      # Configuration management
│   │   └── utils/                       # Core utilities
│   ├── api/                             # API implementations
│   │   ├── provider/                    # Provider factory
│   │   ├── perplexity/                  # Perplexity API implementation
│   │   └── mcp/                         # MCP server integration
│   └── frontend/                        # Frontend implementations
│       └── cli/                         # CLI frontend
├── src/test/kotlin/                     # Test files
├── dataForRag/                          # RAG data files
│   ├── raw/                             # Raw documents for indexing
│   └── indexed/                         # Generated index files
├── build.gradle.kts                     # Gradle build configuration
├── settings.gradle.kts                  # Gradle settings
├── gradlew                             # Gradle wrapper script
└── README.md                           # Project documentation
```