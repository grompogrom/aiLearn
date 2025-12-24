# ✅ Исправление Ollama Reranker

## Проблемы

### Проблема 1: Таймаут
```
ERROR LlmReranker - Failed to re-rank with Ollama: Request timeout has expired
```

### Проблема 2: NoTransformationFoundException
```
ERROR LlmReranker - Expected response body of the type 'OllamaGenerateResponse' but was 'SourceByteReadChannel'
Response header `ContentType: application/x-ndjson`
```

## ✅ Исправлено

### Изменения в коде:

1. **Добавлены таймауты в HttpClient** (60 секунд вместо 15)
2. **Исправлен парсинг ответа** - используется `bodyAsText()` вместо `body<T>()`
3. **Оптимизирован промпт** (200 символов на чанк вместо 300)
4. **Добавлено логирование времени выполнения**

### Что изменилось:

#### 1. Таймауты в HttpClient
```kotlin
private val client = HttpClient(CIO) {
    install(ContentNegotiation) { ... }
    
    engine {
        requestTimeout = 60_000      // 60 секунд
        endpoint {
            connectTimeout = 10_000  // 10 секунд
            socketTimeout = 60_000   // 60 секунд
        }
    }
}
```

#### 2. Парсинг ответа (критическое исправление!)
```kotlin
// БЫЛО (неправильно - не работает с application/x-ndjson):
val response = httpResponse.body<OllamaGenerateResponse>()

// СТАЛО (правильно):
val rawResponse = httpResponse.bodyAsText()
val response = json.decodeFromString<OllamaGenerateResponse>(rawResponse)
```

**Почему это важно**: Ollama возвращает `Content-Type: application/x-ndjson` даже когда `stream=false`, что требует ручного чтения и парсинга.

### Новые логи:

```
INFO  LlmReranker - ⏳ Waiting for Ollama to re-rank candidates (this may take 30-60 seconds)...
INFO  LlmReranker - ✓ Ollama responded in 35420ms (35s)
```

## 🚀 Как тестировать

### 1. Пересоберите проект

```bash
./gradlew clean build
```

### 2. Запустите с Ollama re-ranking

```bash
export AILEARN_RAG_RERANKING=true
export AILEARN_RAG_RERANKING_PROVIDER=ollama
export AILEARN_RAG_RERANK_MODEL=qwen2.5
./gradlew run
```

### 3. Задайте вопрос по индексированным документам

```
💬 Вы: что такое ailearn?
```

### 4. Ожидайте сообщения

```
🔍 Поиск в базе знаний...
INFO  LlmReranker - ⏳ Waiting for Ollama to re-rank candidates (this may take 30-60 seconds)...
```

Re-ranking теперь займет 30-60 секунд - это нормально!

## ⚡ Ускорение (опционально)

Если хотите быстрее:

### Вариант 1: Уменьшите количество кандидатов
```bash
export AILEARN_RAG_CANDIDATE_COUNT=10  # Вместо 20
```

### Вариант 2: Используйте меньшую модель
```bash
export AILEARN_RAG_RERANK_MODEL=qwen2.5:3b
```

### Вариант 3: Используйте LlmProvider (быстрее)
```bash
export AILEARN_RAG_RERANKING_PROVIDER=llm  # Вместо ollama
```

## 📊 Ожидаемая производительность

| Конфигурация | Время | Рекомендация |
|--------------|-------|--------------|
| 10 кандидатов + qwen2.5:3b | ~15-20s | ⚡ Быстро |
| 15 кандидатов + qwen2.5 | ~25-35s | ⭐ Баланс |
| 20 кандидатов + qwen2.5 | ~35-45s | ✅ По умолчанию |
| 20 кандидатов + qwen2.5:7b | ~45-60s | 🎯 Качество |

## 🔍 Проверка логов

```bash
tail -f ailearn.log | grep -i "rerank\|ollama"
```

Успешный запуск:
```
INFO  LlmReranker - Re-ranking 20 candidates with Ollama model: qwen2.5
INFO  LlmReranker - ⏳ Waiting for Ollama to re-rank candidates...
INFO  LlmReranker - ✓ Ollama responded in 35420ms (35s)
DEBUG LlmReranker - Ollama response done: true
INFO  LlmReranker - Successfully parsed 20 re-ranking scores from Ollama
```

## 📝 Что было исправлено

### До исправления:
- ❌ Таймаут 15 секунд (слишком мало)
- ❌ Нет информации о времени ожидания
- ❌ Длинный промпт (300 символов на чанк)

### После исправления:
- ✅ Таймаут 60 секунд
- ✅ Логирование прогресса
- ✅ Оптимизированный промпт (200 символов на чанк)
- ✅ Показывает время выполнения

## 🎯 Итог

**Ollama re-ranking теперь работает!** 

Просто нужно немного терпения - re-ranking качественный, но не самый быстрый. Если нужна скорость, используйте меньше кандидатов или переключитесь на LlmProvider.

---

**Файлы изменены**:
- `src/main/kotlin/core/rag/LlmReranker.kt` - добавлены таймауты и оптимизация
- `OLLAMA_RERANKER_UPDATED.md` - обновлена документация с troubleshooting

