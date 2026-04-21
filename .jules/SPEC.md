# WO-004: Implement Real AI in App 03 — Knowledge Base Vector Search + Q&A

## Scope
1. Introduce embedding generation capabilities to `saas-os-core` and App 03.
2. Automate text chunking and embedding storage on `KbPage` creation and updates.
3. Integrate real vector similarity search using `pgvector`.
4. Replace mocked RAG pipeline in `AiKnowledgeService` with actual OpenAI-compatible Gateway calls.
5. Upgrade `SearchController` to respect `type=semantic` and use similarity search.
6. Implement graceful degradation to handle AI gateway unreachability.

## Implementation Details
1. **saas-os-core**:
   - `EmbeddingRequest.java` & `EmbeddingResponse.java` for LiteLLM schema.
   - Extend `LiteLlmApi` with `/embeddings`.
   - Expose `callLlmRaw(String prompt)` in `AiService` to run custom RAG prompts.
2. **App 03**:
   - `EmbeddingService`: handles AI gateway communication, handles errors gracefully.
   - `KbPageService`: `indexPage(KbPage)` method invoked on create/update to clear old chunks, split text into 500-word chunks (50-word overlap), generate embeddings, and save them.
   - `AiKnowledgeService.askQuestion()`: generate embedding for question -> retrieve top 5 chunks -> prompt `AiService.callLlmRaw` -> save `AiQaSession`.
   - `SearchController`: for `semantic` search type, get query embedding -> fetch similar chunks -> extract distinct `KbPage`s. If AI is down, fallback to `keyword`.

## Acceptance Criteria
- Pages auto-chunk and embed on save.
- `POST /api/v1/ai/ask` uses vector search + real LLM answer.
- `GET /api/v1/search?type=semantic` searches by meaning.
- Unreachable Gateway leads to graceful fallback (keyword search / error note in AI answer) without 500s.
- Monorepo builds pass.
