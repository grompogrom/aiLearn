# RAG Re-ranking Quick Reference

## 🚀 Quick Start

### Enable Re-ranking (Ollama - Recommended)

```bash
export AILEARN_RAG_RERANKING=true
export AILEARN_RAG_RERANKING_PROVIDER=ollama
export AILEARN_RAG_RERANK_MODEL=qwen2.5
./gradlew run
```

### Enable Re-ranking (LlmProvider - Higher Quality)

```bash
export AILEARN_RAG_RERANKING=true
export AILEARN_RAG_RERANKING_PROVIDER=llm
./gradlew run
```

### Disable Re-ranking (Default)

```bash
export AILEARN_RAG_RERANKING=false
# or don't set any re-ranking variables
./gradlew run
```

## 📊 Configuration Options

| Variable | Values | Default | Description |
|----------|--------|---------|-------------|
| `AILEARN_RAG_RERANKING` | `true`/`false` | `false` | Enable/disable re-ranking |
| `AILEARN_RAG_RERANKING_PROVIDER` | `ollama`/`llm` | `ollama` | Which LLM to use |
| `AILEARN_RAG_CANDIDATE_COUNT` | integer | `20` | Number of candidates to re-rank |
| `AILEARN_RAG_RERANK_MODEL` | string | `qwen2.5` | Ollama model name |

## 🎯 How It Works

```
User Query
    ↓
1. Embed query (Ollama)
    ↓
2. Cosine similarity → Top 20 candidates
    ↓
3. LLM re-ranks candidates (if enabled)
    ↓
4. Select top-K (e.g., 3) best chunks
    ↓
5. Generate answer with context
```

## 📈 Expected Output

### With Re-ranking Enabled

```
🔍 Поиск в базе знаний...

📚 Найдено релевантных фрагментов: 3
  1. [README.md] Cosine: 0.87 → LLM: 0.94
  2. [ARCHITECTURE.md] Cosine: 0.82 → LLM: 0.89
  3. [README.md.1] Cosine: 0.78 → LLM: 0.85

🤖 Ответ:
[Answer based on re-ranked context]
```

### Without Re-ranking (Default)

```
🔍 Поиск в базе знаний...

📚 Найдено релевантных фрагментов: 3
  1. [README.md] Релевантность: 0.89
  2. [ARCHITECTURE.md] Релевантность: 0.82
  3. [README.md.1] Релевантность: 0.78

🤖 Ответ:
[Answer based on cosine similarity]
```

## 🔧 Properties File Configuration

Alternative to environment variables, add to `ailearn.config.properties`:

```properties
rag.reranking=true
rag.reranking.provider=ollama
rag.candidate.count=20
rag.rerank.model=qwen2.5
```

## ⚡ Performance

| Provider | Speed | Cost | Quality |
|----------|-------|------|---------|
| Ollama | Medium (~30-45s for 20 candidates) | Free | Good |
| LlmProvider | Fast (~3-10s) | API costs | Excellent |
| Disabled | Very Fast (~1ms) | Free | Baseline |

**Note**: Ollama re-ranking time depends on:
- Number of candidates (10-20)
- Model size (3b, 7b, etc.)
- Hardware (CPU/GPU)

## 💡 When to Use

**Use Ollama Re-ranking when**:
- You want better results without API costs
- Speed is important (local processing)
- You have Ollama running locally

**Use LlmProvider Re-ranking when**:
- Maximum accuracy is critical
- API costs are acceptable
- Complex queries with nuanced meaning

**Disable Re-ranking when**:
- Speed is critical (real-time applications)
- Simple keyword-based queries
- Testing or development

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| "Model not found" | Run `ollama pull qwen2.5` |
| "Request timeout" | Normal for 20+ candidates. Wait up to 60s. Reduce candidates if needed. |
| Re-ranking fails | Check logs, falls back to cosine scores automatically |
| Slow performance | Reduce candidates (`AILEARN_RAG_CANDIDATE_COUNT=10`) or use smaller model |
| No improvement | May need more candidates or different model |
| Connection refused | Ensure Ollama is running (`ollama serve`) |

## 📚 Related Documentation

- `RAG_RERANKING_IMPLEMENTATION.md` - Full implementation details
- `RAG_IMPLEMENTATION_SUMMARY.md` - Original RAG system
- `QUICK_START_RAG.md` - General RAG quick start

---

**Status**: ✅ Ready to use!

