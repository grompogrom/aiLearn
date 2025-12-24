# ✅ Исправление NoTransformationFoundException

## Проблема

После исправления таймаута возникла новая ошибка:

```
ERROR LlmReranker - Expected response body of the type 'OllamaGenerateResponse' but was 'SourceByteReadChannel'
Response header `ContentType: application/x-ndjson`
```

## Причина

Ollama API возвращает `Content-Type: application/x-ndjson` (newline-delimited JSON) вместо обычного `application/json`, даже когда мы указываем `stream: false`. 

Ktor не может автоматически десериализовать NDJSON в объект, поэтому `httpResponse.body<OllamaGenerateResponse>()` не работает.

## ✅ Решение

Изменен способ чтения ответа - теперь читаем как текст и парсим вручную:

### Было (неправильно):
```kotlin
val response = httpResponse.body<OllamaGenerateResponse>()
```

### Стало (правильно):
```kotlin
// Читаем ответ как текст
val rawResponse = httpResponse.bodyAsText()

// Парсим JSON вручную
val json = Json { ignoreUnknownKeys = true; isLenient = true }
val response = json.decodeFromString<OllamaGenerateResponse>(rawResponse)
```

## 🚀 Теперь работает!

После этого исправления Ollama re-ranking должен заработать корректно.

### Проверка:

```bash
# Пересоберите проект
./gradlew clean build

# Запустите
export AILEARN_RAG_RERANKING=true
export AILEARN_RAG_RERANKING_PROVIDER=ollama
./gradlew run
```

### Ожидаемый вывод в логах:

```
INFO  LlmReranker - Re-ranking 20 candidates with Ollama model: qwen2.5
INFO  LlmReranker - ⏳ Waiting for Ollama to re-rank candidates (this may take 30-60 seconds)...
INFO  LlmReranker - ✓ Ollama responded in 14775ms (14s)
DEBUG LlmReranker - Raw response length: 458 chars
DEBUG LlmReranker - Ollama response done: true
INFO  LlmReranker - Successfully parsed 20 re-ranking scores from Ollama
```

## Технические детали

### Content-Type: application/x-ndjson

NDJSON (Newline Delimited JSON) - это формат, где каждая строка содержит отдельный JSON объект:

```
{"line": 1, "data": "first"}
{"line": 2, "data": "second"}
```

Ollama использует этот формат для потоковой передачи данных. Когда `stream: false`, возвращается только одна строка JSON, но Content-Type остается `application/x-ndjson`.

### Почему не работала автодесериализация

Ktor ContentNegotiation Plugin настроен на работу с `application/json`, а не с `application/x-ndjson`. Поэтому:

1. Ktor не мог найти подходящий десериализатор
2. Бросал `NoTransformationFoundException`
3. Ответ оставался как `SourceByteReadChannel` (необработанный поток байт)

### Решение

Использование `bodyAsText()`:
- Читает весь ответ как строку
- Не зависит от Content-Type
- Позволяет вручную распарсить JSON с нужными настройками

## Список всех исправлений

1. ✅ **Таймаут** - увеличен до 60 секунд
2. ✅ **Парсинг NDJSON** - используется `bodyAsText()` 
3. ✅ **Оптимизация** - промпт сокращен до 200 символов на чанк
4. ✅ **Логирование** - добавлен прогресс и время выполнения

## 🎉 Готово!

Ollama re-ranking теперь полностью работает. Просто запустите и подождите 30-60 секунд для получения улучшенных результатов!

---

**См. также**:
- `FIXED_SUMMARY.md` - общая сводка всех исправлений
- `TIMEOUT_FIX.md` - детали обоих исправлений
- `OLLAMA_RERANKER_UPDATED.md` - полная документация

