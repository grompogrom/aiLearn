# RAG Indexing - Quick Start Guide

## ⚡ Quick Test (30 seconds)

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

### 4. Verify Output
```bash
cat dataForRag/indexed/index.json | jq '.model, .chunks | length'
```
Expected:
```
"mxbai-embed-large"
380
```

## 🎯 What Was Built

| Component | Purpose | Location |
|-----------|---------|----------|
| **OllamaClient** | Calls Ollama API | `api/ollama/OllamaClient.kt` |
| **DocumentChunker** | Splits text | `core/rag/DocumentChunker.kt` |
| **IndexingService** | Orchestrates pipeline | `core/rag/IndexingService.kt` |
| **RagStorage** | Saves/loads JSON | `core/rag/RagStorage.kt` |
| **CLI Command** | `/index` handler | `frontend/cli/CliFrontend.kt` |

## 📊 Processing Details

- **Input**: 4 .md files (~190 KB total)
- **Chunking**: 500 chars, 50 overlap → ~380 chunks
- **Embedding**: mxbai-embed-large (1024 dims)
- **Output**: JSON file (~12 MB with embeddings)

## 🔍 Troubleshooting

| Issue | Solution |
|-------|----------|
| "Ollama не настроена" | Start Ollama: `ollama serve` |
| "Connection refused" | Check: `http://127.0.0.1:11434` |
| "Model not found" | Pull model: `ollama pull mxbai-embed-large` |
| "No .md files found" | Check `dataForRag/raw/` has files |

## 📝 Available Commands

| Command | Description |
|---------|-------------|
| `/index` | Build RAG index from documents |
| `/clear` | Clear conversation history |
| `/mcp` | Show MCP tools |
| `/reminder` | Toggle reminder checks |
| `exit` | Quit application |

## 🚀 What's Next?

Now you can implement:
1. **Query/Search**: Find relevant chunks for user questions
2. **RAG Integration**: Augment LLM prompts with retrieved context
3. **Incremental Updates**: Re-index only changed files
4. **Multiple Models**: Support different embedding models

## 📖 Full Documentation

- `RAG_IMPLEMENTATION_SUMMARY.md` - Complete implementation details
- `TEST_RAG_PIPELINE.md` - Detailed testing guide
- Plan file - Original design specification

---

**Status**: ✅ Ready to use! Run `/index` to create your first RAG index.

