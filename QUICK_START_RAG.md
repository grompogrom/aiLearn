# RAG System - Quick Start Guide

## ⚡ Quick Test (60 seconds)

### 1. Check Ollama
```bash
curl -X POST http://localhost:11434/api/embed \
  -H "Content-Type: application/json" \
  -d '{"model": "mxbai-embed-large", "input": ["test"]}'
```
✅ Should return JSON with embeddings

### 2. Run Application
```bash
./gradlew run
```

### 3. Create Index
At the prompt, type:
```
/index
```

### 4. Verify Index
```bash
cat dataForRag/indexed/index.json | jq '.model, .chunks | length'
```
Expected:
```
"mxbai-embed-large"
380
```

### 5. Ask a Question
At the prompt, type:
```
/ask What is RAG?
```

Expected output:
```
🔍 Поиск в базе знаний...
📚 Найдено релевантных фрагментов: 3
  1. [README.md] Релевантность: 0.89
  2. [ARCHITECTURE.md] Релевантность: 0.82
  3. [RAG_IMPLEMENTATION_SUMMARY.md] Релевантность: 0.78
🤖 Ответ:
[LLM's context-aware answer]
```

## 🎯 What Was Built

| Component | Purpose | Location |
|-----------|---------|----------|
| **OllamaClient** | Calls Ollama API | `api/ollama/OllamaClient.kt` |
| **DocumentChunker** | Splits text | `core/rag/DocumentChunker.kt` |
| **IndexingService** | Orchestrates indexing | `core/rag/IndexingService.kt` |
| **RagStorage** | Saves/loads JSON | `core/rag/RagStorage.kt` |
| **SimilaritySearch** | Cosine similarity & top-K | `core/rag/SimilaritySearch.kt` |
| **RagQueryService** | Orchestrates RAG queries | `core/rag/RagQueryService.kt` |
| **CLI Commands** | `/index` and `/ask` | `frontend/cli/CliFrontend.kt` |

## 📊 System Details

**Indexing:**
- **Input**: 4 .md files (~190 KB total)
- **Chunking**: 500 chars, 50 overlap → ~380 chunks
- **Embedding**: mxbai-embed-large (1024 dims)
- **Output**: JSON file (~12 MB with embeddings)

**Querying:**
- **Similarity**: Cosine similarity between vectors
- **Retrieval**: Top-3 most relevant chunks
- **Context**: Formatted with source + relevance score
- **LLM**: Uses AppConfig settings (model, temperature, maxTokens)

## 🔍 Troubleshooting

| Issue | Solution |
|-------|----------|
| "Ollama не настроена" | Start Ollama: `ollama serve` |
| "Connection refused" | Check: `http://127.0.0.1:11434` |
| "Model not found" | Pull model: `ollama pull mxbai-embed-large` |
| "No .md files found" | Check `dataForRag/raw/` has files |
| "RAG index not found" | Run `/index` first to build the index |
| "RAG сервис недоступен" | Ensure Ollama client is configured |

## 📝 Available Commands

| Command | Description |
|---------|-------------|
| `/index` | Build RAG index from documents |
| `/ask <question>` | Query the knowledge base with context-aware answers |
| `/rag <question>` | Alias for `/ask` command |
| `/clear` | Clear conversation history |
| `/mcp` | Show MCP tools |
| `/reminder` | Toggle reminder checks |
| `exit` | Quit application |

## 🚀 How to Use

**Basic Workflow:**
1. Run `/index` once to build your knowledge base
2. Ask questions anytime with `/ask <your question>`
3. Get answers with source attribution and relevance scores

**Example Questions:**
- `/ask What is Clean Architecture?`
- `/ask How does the MCP integration work?`
- `/ask What are the main components of this system?`
- `/rag Explain the RAG pipeline`

**Advanced Usage:**
- Re-run `/index` after adding new documents to `dataForRag/raw/`
- Check indexed sources by examining `dataForRag/indexed/index.json`
- Adjust LLM settings via environment variables (temperature, model, etc.)

## 📖 Full Documentation

- `RAG_IMPLEMENTATION_SUMMARY.md` - Complete implementation details
- `TEST_RAG_PIPELINE.md` - Detailed testing guide
- Plan file - Original design specification

---

**Status**: ✅ Fully operational! 
- Run `/index` to build your knowledge base
- Run `/ask <question>` to get context-aware answers

