# WO-004: Implement Real AI in App 03 — Knowledge Base Vector Search + Q&A

## Problem
App 03 (`apps/03-ai-knowledge-base`) has a complete pgvector schema (`page_chunks` table with `embedding vector(1536)` column, ivfflat index) but the AI Q&A and semantic search are fully mocked.

`AiKnowledgeService.askQuestion()` returns a hardcoded string.
`SearchController` does keyword search only and ignores the `type=semantic` parameter.

The LiteLLM gateway (at `${ai.gateway-url:http://localhost:4000}`) is the AI backend, already wired via `LiteLlmApi` in saas-os-core.

## What to Build

### 1. Embedding Generation — `EmbeddingService`

Create `apps/03-ai-knowledge-base/src/main/java/com/changelog/service/EmbeddingService.java`:

```java
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final LiteLlmApi liteLlmApi;
    
    @Value("${ai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;
    
    // Call the /embeddings endpoint on LiteLLM gateway
    public float[] generateEmbedding(String text) {
        // POST to /embeddings with model + input
        // Return float[] of 1536 dimensions
    }
    
    // Convert float[] to PGvector for storage
    public com.pgvector.PGvector toPGvector(float[] embedding) {
        return new com.pgvector.PGvector(embedding);
    }
}
```

The LiteLLM gateway exposes a `/embeddings` endpoint compatible with the OpenAI embeddings API format. You need to add a new Retrofit interface method to `LiteLlmApi` in saas-os-core:

Add to `saas-os-core/src/main/java/com/changelog/ai/LiteLlmApi.java`:
```java
@POST("embeddings")
Call<EmbeddingResponse> createEmbedding(@Body EmbeddingRequest request);
```

Create `saas-os-core/src/main/java/com/changelog/ai/EmbeddingRequest.java`:
```java
public class EmbeddingRequest {
    private String model;
    private String input;  // single text string
    // constructors, getters
}
```

Create `saas-os-core/src/main/java/com/changelog/ai/EmbeddingResponse.java`:
```java
public class EmbeddingResponse {
    private List<EmbeddingData> data;
    // EmbeddingData has float[] embedding
}
```

### 2. Auto-Chunk Pages on Save — `KbPageService`

When a page is created or content is updated in `KbPageService`, chunk the content and generate embeddings:

```
private void indexPage(KbPage page) {
    // 1. Delete existing chunks for this page
    pageChunkRepository.deleteByPageId(page.getId());
    
    // 2. Split content into ~500-token chunks with 50-token overlap
    List<String> chunks = chunkText(page.getContent(), 500);
    
    // 3. For each chunk: generate embedding and save PageChunk
    for (int i = 0; i < chunks.size(); i++) {
        float[] embedding = embeddingService.generateEmbedding(chunks.get(i));
        PageChunk chunk = PageChunk.builder()
            .pageId(page.getId())
            .chunkIndex(i)
            .content(chunks.get(i))
            .embedding(embeddingService.toPGvector(embedding))
            .build();
        pageChunkRepository.save(chunk);
    }
}
```

Chunking can use a simple word-boundary split — no external library needed:
```java
private List<String> chunkText(String text, int maxWords) {
    // Split on whitespace, group into maxWords chunks with 50-word overlap
}
```

### 3. Vector Similarity Search — `PageChunkRepository`

Check the existing `PageChunkRepository`:
`apps/03-ai-knowledge-base/src/main/java/com/changelog/repository/PageChunkRepository.java`

Add or verify this native query exists (using pgvector `<=>` cosine distance operator):
```java
@Query(value = """
    SELECT pc.* FROM page_chunks pc
    JOIN kb_pages p ON pc.page_id = p.id
    WHERE p.tenant_id = :tenantId
    ORDER BY pc.embedding <=> CAST(:embedding AS vector)
    LIMIT :limit
    """, nativeQuery = true)
List<PageChunk> findSimilarChunks(
    @Param("tenantId") UUID tenantId,
    @Param("embedding") String embedding,  // pgvector expects string format: '[0.1,0.2,...]'
    @Param("limit") int limit
);
```

Note: the `embedding` param must be passed as a string in pgvector format: `"[0.12, 0.34, ...]"`.
Convert float[] to this format with `Arrays.toString(floats).replace(", ", ",")`.

### 4. RAG Q&A — `AiKnowledgeService`

Replace the hardcoded stub in `AiKnowledgeService.askQuestion()`:

```java
public AiQaSession askQuestion(UUID tenantId, UUID userId, String question) {
    // 1. Generate embedding for the question
    float[] questionEmbedding = embeddingService.generateEmbedding(question);
    String embeddingStr = Arrays.toString(questionEmbedding);
    
    // 2. Retrieve top-5 relevant chunks
    List<PageChunk> relevantChunks = pageChunkRepository.findSimilarChunks(
        tenantId, embeddingStr, 5
    );
    
    // 3. Build context from chunks
    String context = relevantChunks.stream()
        .map(PageChunk::getContent)
        .collect(Collectors.joining("\n\n---\n\n"));
    
    // 4. Build prompt and call LLM
    String prompt = String.format("""
        You are a helpful assistant. Answer the question based ONLY on the context provided below.
        If the answer is not in the context, say "I don't have information about that in this knowledge base."
        
        Context:
        %s
        
        Question: %s
        
        Answer:
        """, context, question);
    
    // 5. Call AiService or LiteLlmApi directly
    // Use AiService.callLlm() if visible, or build request manually
    
    // 6. Save and return AiQaSession with answer and citations
    // Citations: list of {pageId, title, excerpt} from relevantChunks
}
```

`AiService` in saas-os-core has a private `callLlm()` method. Since it's private, either:
- Make it package-protected or add a `public String callLlmRaw(String prompt)` method to `AiService`, OR
- Inject `LiteLlmApi` directly into `AiKnowledgeService` and make the completion call manually

Prefer adding `public String callLlmRaw(String prompt)` to `AiService` in saas-os-core.

### 5. Semantic Search Endpoint — `SearchController`

The existing `GET /api/v1/search?q=...&type=keyword|semantic` ignores `type=semantic`.

Fix `SearchController`:
```java
if ("semantic".equals(type)) {
    float[] queryEmbedding = embeddingService.generateEmbedding(q);
    String embStr = Arrays.toString(queryEmbedding);
    List<PageChunk> chunks = pageChunkRepository.findSimilarChunks(tenantId, embStr, 10);
    // Group chunks by page, return distinct pages with matched excerpt
} else {
    // existing keyword search with PostgreSQL tsvector — keep as-is
}
```

### 6. Graceful Degradation
If `LiteLlmApi` call fails (gateway is down), catch the exception and:
- For embedding: log error, return null embedding, save chunk without embedding
- For Q&A: fall back to keyword search and note "AI unavailable" in the answer field
- Do NOT throw 500 to the client

## Acceptance Criteria
1. Creating/updating a KB page triggers chunk generation + embedding storage in `page_chunks`
2. `POST /api/v1/ai/ask` returns a non-hardcoded answer when the LiteLLM gateway is reachable
3. `GET /api/v1/search?type=semantic&q=...` uses vector similarity, not keyword FTS
4. `GET /api/v1/search?type=keyword&q=...` still uses the existing FTS path unchanged
5. If the AI gateway is unreachable, endpoints return 200 with a graceful fallback response (not 500)
6. `mvn compile -pl saas-os-core,apps/03-ai-knowledge-base` succeeds
7. Rebuild saas-os-core after API changes: `mvn install -pl saas-os-core`

## Tech Stack
- Java 21, Spring Boot 3.3.5
- PostgreSQL 16 with pgvector extension (already enabled in the running DB)
- `com.pgvector:pgvector:0.1.4` — already in App 03's pom.xml
- `PGvector` class from `com.pgvector` — wraps float[] for pgvector column storage
- Retrofit2 for LiteLLM API calls (already used in saas-os-core)
- `LiteLlmApi` is at `saas-os-core/src/main/java/com/changelog/ai/LiteLlmApi.java`
- LiteLLM gateway is OpenAI-API compatible — embeddings endpoint format: `POST /embeddings` with `{"model": "text-embedding-3-small", "input": "text here"}`
- The embedding dimension is 1536 (OpenAI text-embedding-3-small) — matches the `vector(1536)` column in the schema
- `@Transactional` on `indexPage()` is required — chunk deletion + re-insertion must be atomic
- Do NOT use Spring AI, LangChain4j, or any new dependencies not already in the pom — implement directly with Retrofit2 and pgvector
