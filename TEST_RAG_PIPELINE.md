# RAG Indexing Pipeline - Testing Guide

## Implementation Complete ✅

All components have been successfully implemented:

1. **OllamaClient** (`api/ollama/OllamaClient.kt`) - HTTP client for Ollama embeddings API
2. **OllamaModels** (`api/ollama/OllamaModels.kt`) - Request/response data models
3. **RagModels** (`core/rag/RagModels.kt`) - Chunk, EmbeddedChunk, RagIndex models
4. **DocumentChunker** (`core/rag/DocumentChunker.kt`) - Fixed-size text chunking with overlap
5. **RagStorage** (`core/rag/RagStorage.kt`) - JSON serialization for index storage
6. **IndexingService** (`core/rag/IndexingService.kt`) - Pipeline orchestrator
7. **CliFrontend** - Added `/index` command handler
8. **Main.kt** - Wired up all components

## How to Test

### Prerequisites
1. Ensure Ollama is running: `http://127.0.0.1:11434`
2. Ensure the model is available. Test with:
```bash
curl -X POST http://localhost:11434/api/embed \
  -H "Content-Type: application/json" \
  -d '{
    "model": "mxbai-embed-large",
    "input": ["test"]
  }'
```

### Running the Indexing Pipeline

1. Start the application:
```bash
./gradlew run
```

2. When the prompt appears, type:
```
/index
```

3. You should see progress messages like:
```
=== Создание RAG индекса ===
🔍 Scanning for .md files...
📚 Found 4 documents: README.md, README.md.1, README.md.2, README.md.3
✂️ Splitting documents into chunks...
📝 Generated X chunks
🧠 Generating embeddings with model: mxbai-embed-large...
   Processing batch 1/Y (10 chunks)...
   Processing batch 2/Y (10 chunks)...
✅ Generated X embeddings
💾 Saving index...
✅ Index saved successfully! Total chunks: X
Индекс сохранен в: dataForRag/indexed/index.json
========================
```

### Verifying the Results

1. Check that the index file was created:
```bash
ls -lh dataForRag/indexed/index.json
```

2. Inspect the index structure:
```bash
head -n 50 dataForRag/indexed/index.json
```

Expected structure:
```json
{
  "model": "mxbai-embed-large",
  "createdAt": "2025-12-22T...",
  "chunks": [
    {
      "text": "chunk text...",
      "source": "README.md",
      "position": 0,
      "embedding": [0.123, -0.456, ...]
    },
    ...
  ]
}
```

3. Verify embedding dimensions (should be 1024 for mxbai-embed-large):
```bash
cat dataForRag/indexed/index.json | jq '.chunks[0].embedding | length'
```

## Configuration

Default settings (in IndexingService):
- **Model**: `mxbai-embed-large`
- **Chunk size**: 500 characters
- **Overlap**: 50 characters
- **Batch size**: 10 chunks per API call
- **Source directory**: `dataForRag/raw`
- **Output directory**: `dataForRag/indexed`

## Error Handling

If Ollama is not running, you'll see:
```
✗ Ошибка при создании индекса: Failed to generate embeddings from Ollama: ...
Убедитесь, что:
  - Ollama запущена (http://127.0.0.1:11434)
  - Модель mxbai-embed-large доступна
  - Директория dataForRag/raw содержит .md файлы
```

## Architecture

```
User types /index
    ↓
CliFrontend.handleIndexCommand()
    ↓
IndexingService.buildIndex()
    ↓
1. Load .md files from dataForRag/raw
2. DocumentChunker splits texts into chunks (500 chars, 50 overlap)
3. OllamaClient generates embeddings (batches of 10)
4. RagStorage saves index as JSON to dataForRag/indexed/
```

## Next Steps

After indexing is complete, you can:
1. Load the index using `RagStorage.loadIndex()`
2. Implement similarity search using cosine similarity
3. Integrate with the conversation manager for RAG-based responses
4. Build a retrieval system that finds relevant chunks for user queries

