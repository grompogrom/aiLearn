# RAG Pipeline Testing - Quick Reference Guide

## Running Tests

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test File
```bash
# Document chunking tests
./gradlew test --tests "core.rag.DocumentChunkerTest"

# Ollama client tests
./gradlew test --tests "api.ollama.OllamaClientTest"

# Ollama models tests
./gradlew test --tests "api.ollama.OllamaModelsTest"

# RAG models tests
./gradlew test --tests "core.rag.RagModelsTest"

# Storage tests
./gradlew test --tests "core.rag.RagStorageTest"

# Indexing service tests
./gradlew test --tests "core.rag.IndexingServiceTest"

# Integration tests
./gradlew test --tests "core.rag.RagIntegrationTest"
```

### Run Specific Test Method
```bash
./gradlew test --tests "core.rag.DocumentChunkerTest.test basic chunking with default parameters"
```

### Run Tests with Detailed Output
```bash
./gradlew test --info
```

### Run Tests in Continuous Mode
```bash
./gradlew test --continuous
```

## Test Categories

### Unit Tests (Fast, Isolated)
- **DocumentChunkerTest** - Text splitting logic
- **OllamaModelsTest** - Data model serialization
- **RagModelsTest** - RAG data structures
- **RagStorageTest** - File I/O operations

### Service Tests (With Mocking)
- **OllamaClientTest** - HTTP client with mock server
- **IndexingServiceTest** - Orchestration with mocked dependencies

### Integration Tests (End-to-End)
- **RagIntegrationTest** - Complete pipeline flow

## Test Reports

### View HTML Report
After running tests:
```bash
open build/reports/tests/test/index.html
```

Or manually navigate to:
```
build/reports/tests/test/index.html
```

### View XML Reports
JUnit XML reports are in:
```
build/test-results/test/
```

## Debugging Tests

### Run Single Test with Stack Traces
```bash
./gradlew test --tests "core.rag.DocumentChunkerTest" --stacktrace
```

### Run with Full Debug Info
```bash
./gradlew test --tests "core.rag.DocumentChunkerTest" --debug
```

### Run Failed Tests Only
```bash
./gradlew test --rerun-tasks --tests "*.failing.test.pattern"
```

## Common Test Patterns

### Testing Serialization
```kotlin
val original = MyDataClass(...)
val serialized = json.encodeToString(original)
val deserialized = json.decodeFromString<MyDataClass>(serialized)
assertEquals(original, deserialized)
```

### Testing with Temp Files
```kotlin
@TempDir
lateinit var tempDir: Path

@Test
fun `test with temp directory`() {
    val testFile = tempDir.resolve("test.txt").toFile()
    testFile.writeText("content")
    // Test operations...
}
```

### Testing Coroutines
```kotlin
@Test
fun `test async operation`() = runBlocking {
    val result = suspendingFunction()
    assertEquals(expected, result)
}
```

### Mocking with MockK
```kotlin
val mockClient = mockk<OllamaClient>()
coEvery { mockClient.embedText(any(), any()) } returns listOf(listOf(0.1f))
```

### Mocking HTTP with Ktor
```kotlin
val mockClient = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            respond(
                content = """{"response": "data"}""",
                status = HttpStatusCode.OK
            )
        }
    }
}
```

## Test Data Examples

### Small Embedding (for fast tests)
```kotlin
val embedding = listOf(0.1f, 0.2f, 0.3f)
```

### Realistic Embedding (1024 dimensions)
```kotlin
val embedding = List(1024) { it * 0.001f }
```

### Test Document Content
```kotlin
val testContent = """
    # Test Document
    
    This is a test document with multiple paragraphs.
    
    ## Section 1
    Content here...
""".trimIndent()
```

### Batch Input
```kotlin
val batchInputs = List(10) { "chunk $it" }
```

## Verifying Test Coverage

### What Each Test File Covers

**DocumentChunkerTest**
- ✅ Splits text into chunks
- ✅ Respects chunk size limits
- ✅ Implements overlap correctly
- ✅ Preserves word boundaries
- ✅ Handles edge cases (empty, short, long text)

**OllamaClientTest**
- ✅ Makes HTTP requests to Ollama API
- ✅ Handles responses correctly
- ✅ Batches multiple inputs
- ✅ Error handling (network errors)
- ✅ Works with mock HTTP client

**OllamaModelsTest**
- ✅ Serializes request/response to JSON
- ✅ Deserializes JSON correctly
- ✅ Handles all data types (strings, floats, lists)
- ✅ Preserves precision and special characters

**RagModelsTest**
- ✅ Core data structures work correctly
- ✅ Serialization roundtrips succeed
- ✅ Handles embeddings of any size
- ✅ Preserves metadata (source, position, timestamp)

**RagStorageTest**
- ✅ Saves index to JSON file
- ✅ Loads index from JSON file
- ✅ Creates directories as needed
- ✅ Handles file not found gracefully
- ✅ Overwrites existing files

**IndexingServiceTest**
- ✅ Orchestrates entire pipeline
- ✅ Processes multiple files
- ✅ Filters by file type (.md only)
- ✅ Batches embedding requests
- ✅ Handles errors gracefully
- ✅ Preserves metadata throughout

**RagIntegrationTest**
- ✅ Full end-to-end pipeline works
- ✅ Data integrity from input to output
- ✅ Multiple files processed correctly
- ✅ Re-indexing updates the index
- ✅ All components work together

## Troubleshooting

### Tests Won't Compile
```bash
# Clean and rebuild
./gradlew clean build

# Check dependencies
./gradlew dependencies --configuration testRuntimeClasspath
```

### Tests Hang or Timeout
- Check for infinite loops in chunking logic
- Verify mock responses are properly configured
- Ensure `runBlocking` is used for coroutines

### Serialization Errors
- Verify `@Serializable` annotation on data classes
- Check JSON field names match exactly
- Ensure `kotlinx.serialization` is configured

### File System Errors
- Verify `@TempDir` is properly annotated
- Check file permissions in test environment
- Ensure cleanup happens in `@AfterEach`

### Mock Not Working
- Verify MockK is imported correctly
- Check `coEvery` vs `every` for suspending functions
- Ensure mock behavior is defined before use

## Performance Benchmarks

Expected test execution times (on modern hardware):

| Test Suite | Expected Duration | Test Count |
|------------|-------------------|------------|
| DocumentChunkerTest | < 1 second | 11 |
| OllamaClientTest | < 2 seconds | 11 |
| OllamaModelsTest | < 2 seconds | 25 |
| RagModelsTest | < 2 seconds | 20 |
| RagStorageTest | < 3 seconds | 17 |
| IndexingServiceTest | < 3 seconds | 21 |
| RagIntegrationTest | < 5 seconds | 14 |
| **Total** | **< 20 seconds** | **119** |

## CI/CD Integration

### GitHub Actions Example
```yaml
name: Test RAG Pipeline
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: ./gradlew test
      - uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-reports
          path: build/reports/tests/
```

### Docker Testing
```dockerfile
FROM gradle:8.5-jdk21
COPY . /app
WORKDIR /app
RUN ./gradlew test
```

## Next Steps After Tests Pass

1. ✅ All tests green? Implementation matches specification
2. 🚀 Implement the actual components (following the test specifications)
3. 🔄 Run tests frequently during development
4. 📊 Check test coverage: `./gradlew test jacocoTestReport`
5. 🎯 Add more tests as you discover edge cases

## Useful Gradle Tasks

```bash
# Run tests and generate coverage report
./gradlew test jacocoTestReport

# Run only fast tests (unit tests)
./gradlew test --tests "*Test" --exclude-tests "*IntegrationTest"

# Clean test cache and rerun
./gradlew cleanTest test

# Show test output in real-time
./gradlew test --console=plain

# Run tests in parallel (faster)
./gradlew test --parallel --max-workers=4
```

## IDE Integration

### IntelliJ IDEA
- Right-click test file → "Run 'TestClassName'"
- Use the green ▶️ icon next to test methods
- View results in the Run panel
- Debug tests by clicking 🐛 icon

### VS Code / Cursor
- Install Kotlin extension
- Use Test Explorer panel
- Click ▶️ to run individual tests
- View results inline

## Need Help?

- 📚 Full test documentation: `RAG_TESTS_SUMMARY.md`
- 📋 Implementation plan: Plan file provided by user
- 🐛 Found a bug in tests? Fix it and document the change
- 💡 Have a test idea? Add it following existing patterns

