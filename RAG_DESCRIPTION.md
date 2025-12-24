# RAG System - Complete Documentation

## 📋 Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Implementation Details](#implementation-details)
- [LLM Re-ranking](#llm-re-ranking)
- [Configuration](#configuration)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

## Overview

The aiLearn RAG (Retrieval-Augmented Generation) system enables context-aware AI responses by indexing and searching through your knowledge base. The system uses Ollama for embeddings and supports two-stage retrieval with optional LLM re-ranking.

### Key Features

- **Document Indexing**: Automatically indexes markdown files from `dataForRag/raw/`
- **Semantic Search**: Uses vector embeddings for semantic similarity matching
- **LLM Re-ranking**: Optional two-stage retrieval for improved relevance
- **Context-Aware Answers**: LLM generates answers based on retrieved context
- **Source Attribution**: Shows which documents were used with relevance scores

## Quick Start

### Prerequisites

1. **Ollama Running**:
```bash
# Check if Ollama is available
curl http://localhost:11434/api/version

# If not running, start it
ollama serve
```

2. **Install Embedding Model**:
```bash
ollama pull mxbai-embed-large
```

### Basic Workflow

1. **Start Application**:
```bash
./gradlew run
```

2. **Build Index** (one-time setup):
```
/index
```

Expected output:
```
=== Создание RAG индекса ===
🔍 Scanning for .md files...
📚 Found 4 documents
✂️ Splitting documents into chunks...
📝 Generated ~380 chunks
🧠 Generating embeddings...
✅ Index saved successfully!
========================
```

3. **Query Knowledge Base**:
```
/ask What is RAG?
```

Expected output:
```
🔍 Поиск в базе знаний...

📚 Найдено релевантных фрагментов: 3
  1. [README.md] Релевантность: 0.89
  2. [ARCHITECTURE.md] Релевантность: 0.82
  3. [RAG_DESCRIPTION.md] Релевантность: 0.78

🤖 Ответ:
[LLM's context-aware answer]
```

## Architecture

### Components

| Component | Purpose | Location |
|-----------|---------|----------|
| **OllamaClient** | Calls Ollama API for embeddings | `api/ollama/OllamaClient.kt` |
| **DocumentChunker** | Splits text into overlapping chunks | `core/rag/DocumentChunker.kt` |
| **IndexingService** | Orchestrates indexing pipeline | `core/rag/IndexingService.kt` |
| **RagStorage** | Saves/loads JSON index | `core/rag/RagStorage.kt` |
| **SimilaritySearch** | Cosine similarity & top-K retrieval | `core/rag/SimilaritySearch.kt` |
| **RagQueryService** | Orchestrates RAG queries | `core/rag/RagQueryService.kt` |
| **LlmReranker** | Optional LLM-based re-ranking | `core/rag/LlmReranker.kt` |

### Data Flow

#### Indexing Pipeline
```
Raw .md Files → Document Chunker → Ollama Embeddings → JSON Index
   (dataForRag/raw/)   (500 chars/50 overlap)   (1024 dims)   (dataForRag/indexed/)
```

#### Query Pipeline (without re-ranking)
```
User Question → Embed Question → Cosine Similarity → Top-K Chunks → Format Context → LLM → Answer
                  (Ollama)        (Fast, 1ms)         (e.g., 3)        (with sources)
```

#### Query Pipeline (with re-ranking)
```
User Question → Embed Question → Cosine Similarity → Top-20 Candidates → LLM Re-rank → Top-K → Answer
                  (Ollama)        (Fast, 1ms)         (20 chunks)       (2-5s)      (e.g., 3)
```

### Index Structure

The index is stored as JSON at `dataForRag/indexed/index.json`:

```json
{
  "model": "mxbai-embed-large",
  "createdAt": "2025-12-24T12:00:00.000Z",
  "chunks": [
    {
      "text": "# RAG System\nRAG (Retrieval-Augmented Generation)...",
      "source": "README.md",
      "position": 0,
      "embedding": [0.012, -0.034, 0.056, ... ] // 1024 dimensions
    }
  ]
}
```

## Implementation Details

### Document Chunking

**Strategy**: Fixed-size chunks with overlap to preserve context

**Parameters**:
- **Chunk Size**: 500 characters
- **Overlap**: 50 characters
- **Boundary Preservation**: Splits at word boundaries when possible

**Example**:
```
Text: "This is a long document with many words that needs to be chunked..."

Chunk 1: "This is a long document with many words that needs..."
Chunk 2: "...that needs to be chunked for processing..."
         ↑ 50 char overlap
```

### Embeddings

**Model**: `mxbai-embed-large`
- **Dimensions**: 1024
- **API**: Ollama `/api/embed`
- **Batch Size**: 10 chunks per request (for efficiency)

**Performance**:
- ~100-200ms per batch of 10 chunks
- Total indexing time: ~30-60 seconds for 380 chunks

### Similarity Search

**Algorithm**: Cosine similarity

```
similarity(A, B) = (A · B) / (||A|| × ||B||)
```

**Implementation**:
1. Calculate dot product of query and chunk embeddings
2. Normalize by vector magnitudes
3. Sort by similarity (descending)
4. Return top-K most similar chunks

**Performance**: ~1ms to search 380 chunks

### Context Formatting

Retrieved chunks are formatted for LLM consumption:

```
## Context from Knowledge Base

### Source: README.md (Relevance: 0.89)
[chunk text]

### Source: ARCHITECTURE.md (Relevance: 0.82)
[chunk text]

### Source: README.md.1 (Relevance: 0.78)
[chunk text]
```

## LLM Re-ranking

### Overview

Two-stage retrieval improves relevance by combining fast vector search with LLM semantic understanding.

### How It Works

1. **Stage 1 (Cosine Search)**: Retrieve 20 candidates quickly (~1ms)
2. **Stage 2 (LLM Re-rank)**: LLM evaluates and scores candidates (~2-45s)
3. **Final Selection**: Top-K best chunks selected for context

### Benefits

- **Better Relevance**: LLM understands context, negations, nuanced meaning
- **Flexible**: Choose between local (Ollama) or cloud (Perplexity) re-ranking
- **Cost-Effective**: Only re-ranks small candidate set (20 chunks)
- **Robust**: Graceful fallback to cosine scores on errors

### Providers

#### Ollama Re-ranker (Recommended)

**Pros**:
- ✅ Free (runs locally)
- ✅ No API costs
- ✅ Privacy-preserving
- ✅ Good quality

**Cons**:
- ⚠️ Slower (30-60s for 20 candidates)
- ⚠️ Requires Ollama running
- ⚠️ Depends on local hardware

**Enable**:
```bash
export AILEARN_RAG_RERANKING=true
export AILEARN_RAG_RERANKING_PROVIDER=ollama
export AILEARN_RAG_RERANK_MODEL=qwen2.5
./gradlew run
```

**Recommended Models**:
| Model | Size | Speed (20 candidates) | Quality |
|-------|------|----------------------|---------|
| qwen2.5:3b | ~2GB | ~20-30s | Good ⚡ |
| qwen2.5 | ~4GB | ~30-45s | Excellent ⭐ |
| qwen2.5:7b | ~4GB | ~40-60s | Excellent 🎯 |

#### LlmProvider Re-ranker

**Pros**:
- ✅ Excellent quality
- ✅ Fast (~3-10s)
- ✅ Stable performance

**Cons**:
- ⚠️ API costs
- ⚠️ Requires internet connection

**Enable**:
```bash
export AILEARN_RAG_RERANKING=true
export AILEARN_RAG_RERANKING_PROVIDER=llm
./gradlew run
```

### Output Examples

**With Re-ranking**:
```
📚 Найдено релевантных фрагментов: 3
  1. [README.md] Cosine: 0.87 → LLM: 0.94
  2. [ARCHITECTURE.md] Cosine: 0.82 → LLM: 0.89
  3. [README.md.1] Cosine: 0.78 → LLM: 0.85
```

**Without Re-ranking**:
```
📚 Найдено релевантных фрагментов: 3
  1. [README.md] Релевантность: 0.89
  2. [ARCHITECTURE.md] Релевантность: 0.82
  3. [README.md.1] Релевантность: 0.78
```

### Re-ranking Prompt

The LLM evaluates chunks with this prompt:

```
Given the user question and list of text chunks, evaluate each chunk's relevance.
Rate each chunk from 0.0 (irrelevant) to 1.0 (highly relevant).

Question: {user_question}

Chunks:
#1: {chunk1_text_preview_200_chars}
#2: {chunk2_text_preview_200_chars}
...

Return ONLY valid JSON array: [{"id": 1, "score": 0.85}, {"id": 2, "score": 0.42}, ...]
```

### Performance Tuning

**For Faster Re-ranking** (Ollama):
```bash
# Use fewer candidates
export AILEARN_RAG_CANDIDATE_COUNT=10

# Use smaller model
export AILEARN_RAG_RERANK_MODEL=qwen2.5:3b
```

**For Better Quality** (Ollama):
```bash
# Use more candidates
export AILEARN_RAG_CANDIDATE_COUNT=20

# Use larger model
export AILEARN_RAG_RERANK_MODEL=qwen2.5:7b
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `AILEARN_RAG_RERANKING` | `false` | Enable/disable re-ranking |
| `AILEARN_RAG_RERANKING_PROVIDER` | `ollama` | Re-ranking provider (`ollama` or `llm`) |
| `AILEARN_RAG_CANDIDATE_COUNT` | `20` | Number of candidates for re-ranking |
| `AILEARN_RAG_RERANK_MODEL` | `qwen2.5` | Ollama model for re-ranking |

### Properties File

Alternative to environment variables, add to `ailearn.config.properties`:

```properties
rag.reranking=true
rag.reranking.provider=ollama
rag.candidate.count=20
rag.rerank.model=qwen2.5
```

### Default Settings

**Indexing**:
- Ollama Host: `http://127.0.0.1:11434`
- Embedding Model: `mxbai-embed-large`
- Chunk Size: 500 characters
- Chunk Overlap: 50 characters
- Batch Size: 10 chunks per API call
- Source Directory: `dataForRag/raw`
- Output Directory: `dataForRag/indexed`
- Output Filename: `index.json`

**Querying**:
- Top-K: 3 chunks (without re-ranking)
- Candidates: 20 chunks (with re-ranking)
- Similarity Algorithm: Cosine similarity
- LLM: Uses AppConfig settings (model, temperature, maxTokens)

## Testing

### Manual Testing

#### 1. Verify Ollama
```bash
curl -X POST http://localhost:11434/api/embed \
  -H "Content-Type: application/json" \
  -d '{"model": "mxbai-embed-large", "input": ["test"]}'
```

✅ Should return JSON with embeddings

#### 2. Build Index
```bash
./gradlew run
# At prompt: /index
```

#### 3. Verify Index
```bash
# Check file size
ls -lh dataForRag/indexed/index.json

# Count chunks
cat dataForRag/indexed/index.json | jq '.chunks | length'

# Verify dimensions
cat dataForRag/indexed/index.json | jq '.chunks[0].embedding | length'
```

Expected:
- File size: ~5-15 MB
- Chunks: ~380 (depends on source files)
- Dimensions: 1024

#### 4. Test Query
```bash
# At prompt:
/ask What is RAG?
```

Expected: Context-aware answer with source attribution

#### 5. Test Re-ranking
```bash
# Enable re-ranking
export AILEARN_RAG_RERANKING=true
export AILEARN_RAG_RERANKING_PROVIDER=ollama
export AILEARN_RAG_RERANK_MODEL=qwen2.5

./gradlew run
# At prompt: /ask What is RAG?
```

Expected: Output shows both cosine and LLM scores

### Automated Testing

**Run all tests**:
```bash
./gradlew test
```

**Run RAG-specific tests** (if implemented):
```bash
./gradlew test --tests "core.rag.*"
```

**View test report**:
```bash
open build/reports/tests/test/index.html
```

### Performance Benchmarks

| Operation | Expected Time | Notes |
|-----------|--------------|-------|
| Index 4 files (~200KB) | 30-60s | Depends on chunk count |
| Cosine similarity search | ~1ms | Very fast |
| Ollama re-ranking (20 chunks) | 30-60s | Depends on model |
| LlmProvider re-ranking | 3-10s | Depends on API |
| Total query (no re-rank) | 3-5s | Includes LLM answer generation |
| Total query (with re-rank) | 35-65s | Includes re-ranking time |

## Troubleshooting

### Indexing Issues

#### "Ollama не настроена" or "Connection refused"
**Solution**: Start Ollama
```bash
ollama serve
```

#### "Model not found"
**Solution**: Pull the model
```bash
ollama pull mxbai-embed-large
```

#### "No .md files found"
**Solution**: Add markdown files to `dataForRag/raw/`

#### Index file is very large (>50MB)
**Cause**: Many documents or large chunks
**Solutions**:
- Reduce chunk size (edit `DocumentChunker.kt`)
- Index fewer documents
- This is usually fine, embeddings are dense

### Query Issues

#### "RAG index not found"
**Solution**: Run `/index` first to build the index

#### "RAG сервис недоступен"
**Solution**: Ensure Ollama is running and configured

#### Retrieved chunks not relevant
**Solutions**:
- Enable re-ranking: `export AILEARN_RAG_RERANKING=true`
- Increase candidate count: `export AILEARN_RAG_CANDIDATE_COUNT=30`
- Re-index with better chunking strategy
- Check if query matches document terminology

### Re-ranking Issues

#### "Request timeout has expired"
**Expected for Ollama**: Re-ranking 20 candidates takes 30-60 seconds
**Solutions**:
- Wait longer (this is normal)
- Reduce candidates: `export AILEARN_RAG_CANDIDATE_COUNT=10`
- Use smaller model: `export AILEARN_RAG_RERANK_MODEL=qwen2.5:3b`
- Use LlmProvider instead: `export AILEARN_RAG_RERANKING_PROVIDER=llm`

#### "NoTransformationFoundException" or "ContentType: application/x-ndjson"
**Status**: ✅ Fixed in current version
**Cause**: Ollama returns `application/x-ndjson` format
**Solution**: Already handled with `bodyAsText()` parsing

#### Re-ranking scores same as cosine scores
**Cause**: Re-ranking failed, fell back to cosine
**Check logs**: Look for `ERROR - Failed to re-rank`
**Solutions**:
- Check Ollama model is available: `ollama list`
- Try different model: `export AILEARN_RAG_RERANK_MODEL=llama3.2`
- Check logs: `tail -f ailearn.log | grep -i rerank`

#### "No JSON array found in response"
**Cause**: Model didn't return proper JSON
**Solutions**:
- Try larger model: `qwen2.5:7b`
- Try different model: `llama3.2`, `mistral`
- Use LlmProvider: `export AILEARN_RAG_RERANKING_PROVIDER=llm`

### Performance Issues

#### Indexing is slow
**Normal**: 30-60 seconds for ~380 chunks
**Optimization**:
- Batch size is already optimized (10 chunks/request)
- Consider fewer/smaller documents
- Use faster hardware with GPU support

#### Queries are slow (with re-ranking)
**Expected**: Ollama re-ranking takes 30-60 seconds
**Solutions**:
- Reduce candidates: `export AILEARN_RAG_CANDIDATE_COUNT=10`
- Use smaller model: `export AILEARN_RAG_RERANK_MODEL=qwen2.5:3b`
- Use LlmProvider re-ranking (~3-10s)
- Disable re-ranking: `export AILEARN_RAG_RERANKING=false`

## CLI Commands

| Command | Description |
|---------|-------------|
| `/index` | Build RAG index from documents |
| `/ask <question>` | Query knowledge base with context-aware answer |
| `/rag <question>` | Alias for `/ask` |
| `/clear` | Clear conversation history |
| `/mcp` | Show MCP tools |
| `/reminder` | Toggle reminder checks |
| `exit` | Quit application |

## Advanced Usage

### Re-indexing After Updates

If you add or modify files in `dataForRag/raw/`:
```
/index
```

This will recreate the index with updated content.

### Inspecting the Index

**View index metadata**:
```bash
cat dataForRag/indexed/index.json | jq '{model, createdAt, chunkCount: (.chunks | length)}'
```

**View first chunk**:
```bash
cat dataForRag/indexed/index.json | jq '.chunks[0]'
```

**Find chunks from specific source**:
```bash
cat dataForRag/indexed/index.json | jq '.chunks[] | select(.source == "README.md")'
```

### Custom Chunking Strategy

Edit `src/main/kotlin/core/rag/DocumentChunker.kt`:

```kotlin
class DocumentChunker(
    private val chunkSize: Int = 500,    // Modify here
    private val overlap: Int = 50        // Modify here
)
```

Then rebuild and re-index:
```bash
./gradlew build
./gradlew run
# At prompt: /index
```

### Using Different Embedding Models

Edit `src/main/kotlin/core/rag/IndexingService.kt`:

```kotlin
private val embeddingModel = "nomic-embed-text"  // Change model
```

Pull the new model:
```bash
ollama pull nomic-embed-text
```

Rebuild and re-index.

## Best Practices

### Document Preparation

1. **Use Markdown**: System is optimized for .md files
2. **Clear Headers**: Use `#` headers for better chunking
3. **Paragraph Spacing**: Add blank lines between paragraphs
4. **Avoid Very Long Lines**: Break text into readable paragraphs
5. **Consistent Formatting**: Helps with boundary detection

### Indexing Strategy

1. **Start Small**: Index a few key documents first
2. **Test Queries**: Verify retrieval quality
3. **Iterate**: Add more documents gradually
4. **Re-index**: After significant updates

### Query Strategy

1. **Be Specific**: "How does RAG re-ranking work?" vs "RAG?"
2. **Use Keywords**: Include terms from your documents
3. **Test Without Re-rank First**: Verify basic retrieval works
4. **Enable Re-rank**: For complex/nuanced queries
5. **Check Sources**: Verify LLM used relevant context

### Re-ranking Strategy

**When to Use Re-ranking**:
- ✅ Complex queries with nuanced meaning
- ✅ Queries with negations ("not", "except", "without")
- ✅ Multi-concept queries
- ✅ When accuracy is critical

**When to Skip Re-ranking**:
- ❌ Simple keyword queries
- ❌ Speed is critical
- ❌ Testing/development
- ❌ Large-scale batch processing

## Future Enhancements

Potential improvements for the RAG system:

### Retrieval
- [ ] Hybrid search (keyword + semantic)
- [ ] Metadata filtering (by date, source, tags)
- [ ] Multi-vector retrieval (different embedding models)
- [ ] Contextual embeddings (sentences + paragraph context)

### Indexing
- [ ] Support more file types (.txt, .pdf, .docx)
- [ ] Incremental updates (re-index only changed files)
- [ ] Semantic section splitting (by headers, topics)
- [ ] Multiple indices for different domains
- [ ] Document metadata extraction

### Re-ranking
- [ ] Hybrid scoring (combine cosine + LLM)
- [ ] Configurable candidate count per query
- [ ] Caching of re-ranking results
- [ ] Batch re-ranking for multiple queries

### User Experience
- [ ] `/sources` command to see indexed documents
- [ ] `/delete-index` command
- [ ] Progress bars for batch operations
- [ ] Preview chunks before generating answer
- [ ] Save/export query results

---

**Status**: ✅ Fully operational!

- Basic RAG: Working
- LLM Re-ranking: Working (Ollama + LlmProvider)
- Ollama Integration: Fixed and tested
- Documentation: Complete

For more details on architecture, see [ARCHITECTURE.md](ARCHITECTURE.md).
For general project information, see [PROJECT_DESCRIPTION.md](PROJECT_DESCRIPTION.md).

