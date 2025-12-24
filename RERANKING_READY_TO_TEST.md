# 🎯 RAG Re-ranking - Готово к тестированию!

## ✅ Что было исправлено

### Проблема
- ❌ Использовался несуществующий эндпоинт `/api/chat`
- ❌ Ollama возвращал 404 ошибку
- ❌ Неправильная структура запроса (chat API вместо generate API)

### Решение
- ✅ Изменен эндпоинт на `/api/generate`
- ✅ Изменена структура запроса на `OllamaGenerateRequest` с полем `prompt`
- ✅ Изменена структура ответа на `OllamaGenerateResponse` с полем `response`
- ✅ Добавлено детальное логирование для диагностики

## 🚀 Как протестировать

### 1. Убедитесь, что Ollama запущена

```bash
# Проверьте, что Ollama работает
curl http://127.0.0.1:11434/api/tags

# Проверьте, что модель установлена
ollama list | grep qwen2.5:3b

# Если модель не установлена:
ollama pull qwen2.5:3b
```

### 2. Пересоберите проект

```bash
cd /Users/vladimir.gromov/Code/AILEARN/aiLearn
./gradlew clean build -x test
```

### 3. Запустите с re-ranking

```bash
export AILEARN_RAG_RERANKING=true
export AILEARN_RAG_RERANKING_PROVIDER=ollama
export AILEARN_RAG_RERANK_MODEL=qwen2.5:3b

./gradlew run
```

### 4. Выполните тестовый запрос

```
/ask What is RAG?
```

### 5. Проверьте результат

**Ожидаемый вывод:**
```
🔍 Поиск в базе знаний...

📚 Найдено релевантных фрагментов: 3
  1. [README.md] Cosine: 0.87 → LLM: 0.94
  2. [ARCHITECTURE.md] Cosine: 0.82 → LLM: 0.89
  3. [README.md.1] Cosine: 0.78 → LLM: 0.85

🤖 Ответ:
[Ответ с учетом re-ranked контекста]
```

**Ключевой момент:** Значения Cosine и LLM должны **отличаться**!

## 📊 Проверка логов

Откройте `ailearn.log` и найдите:

### ✅ Успешное выполнение

```
INFO  - Re-ranking 20 candidates with Ollama model: qwen2.5:3b
DEBUG - Sending request to Ollama: http://127.0.0.1:11434/api/generate
DEBUG - Raw response from Ollama (first 500 chars): {"model":"qwen2.5:3b","response":"[{\"id\":1,\"score\":0.85}...]","done":true}
INFO  - Successfully parsed 3 re-ranking scores from LLM
DEBUG - ID: 1, Score: 0.85
DEBUG - ID: 2, Score: 0.72
DEBUG - ID: 3, Score: 0.91
DEBUG - Created score map with 3 entries: {1=0.85, 2=0.72, 3=0.91}
DEBUG - Chunk 1: originalScore=0.87, llmScore=0.85
DEBUG - Chunk 2: originalScore=0.82, llmScore=0.72
DEBUG - Chunk 3: originalScore=0.78, llmScore=0.91
INFO  - Re-ranking complete, selected top-3 from 20 re-ranked chunks
```

### ❌ Если всё ещё есть проблемы

Проверьте наличие этих строк:
```
ERROR - Failed to re-rank with Ollama, falling back to original scores
```

Если видите ошибку, проверьте:
1. Строку `Raw response from Ollama` - покажите её мне
2. Убедитесь, что модель поддерживает JSON формат
3. Попробуйте другую модель (см. ниже)

## 🔧 Альтернативные модели

Если `qwen2.5:3b` не работает, попробуйте:

### Вариант 1: Llama 3.2
```bash
ollama pull llama3.2:3b
export AILEARN_RAG_RERANK_MODEL=llama3.2:3b
```

### Вариант 2: Mistral
```bash
ollama pull mistral:7b
export AILEARN_RAG_RERANK_MODEL=mistral:7b
```

### Вариант 3: Gemma 2
```bash
ollama pull gemma2:9b
export AILEARN_RAG_RERANK_MODEL=gemma2:9b
```

## 🎯 Альтернатива: LlmProvider

Если Ollama продолжает создавать проблемы, используйте LlmProvider (Perplexity):

```bash
export AILEARN_RAG_RERANKING=true
export AILEARN_RAG_RERANKING_PROVIDER=llm

./gradlew run
```

**Плюсы:**
- ✅ Более надёжно
- ✅ Лучшее качество re-ranking
- ✅ Не требует локальной модели

**Минусы:**
- ❌ API costs (платные запросы)
- ❌ Медленнее

## 📝 Технические детали исправления

### Изменённые файлы
- `src/main/kotlin/core/rag/LlmReranker.kt`

### Ключевые изменения

**1. API Endpoint:**
```kotlin
// Было: POST /api/chat (404)
// Стало: POST /api/generate (✅)
```

**2. Request Structure:**
```kotlin
// Было
data class OllamaChatRequest(
    val messages: List<OllamaMessage>  // ❌
)

// Стало
data class OllamaGenerateRequest(
    val prompt: String  // ✅
)
```

**3. Response Structure:**
```kotlin
// Было
data class OllamaChatResponse(
    val message: OllamaMessage  // ❌
)

// Стало
data class OllamaGenerateResponse(
    val response: String  // ✅
)
```

## 📚 Документация

- `RAG_RERANKING_FIX.md` - Подробное описание исправления
- `RAG_RERANKING_IMPLEMENTATION.md` - Полная документация функции
- `RAG_RERANKING_QUICK_REFERENCE.md` - Краткая справка

## 🎉 Готово!

Все исправления применены и скомпилированы. Приложение готово к тестированию!

---

**Дата**: 2025-12-24  
**Статус**: ✅ Готово к тестированию  
**Компиляция**: ✅ Успешна

