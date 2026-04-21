1. **Core Changes (saas-os-core)**
   - Add `EmbeddingRequest`, `EmbeddingResponse` to `com.changelog.ai`.
   - Update `LiteLlmApi` to include `@POST("embeddings")`.
   - Add public `callLlmRaw(String prompt)` to `AiService`.
   - Rebuild core module.

2. **EmbeddingService (App 03)**
   - Create `EmbeddingService` in `apps/03-ai-knowledge-base/src/main/java/com/changelog/service/EmbeddingService.java`.
   - Implement `generateEmbedding(String text)` with graceful error handling (returns null on error).
   - Implement `toPGvector(float[])`.

3. **KbPageService (App 03)**
   - Add `indexPage(KbPage page)` method in `KbPageService`.
   - Implement text chunking logic (500 words, 50 word overlap).
   - Call `indexPage` at the end of `createPage` and `updatePage`.

4. **PageChunkRepository Check (App 03)**
   - Verify/Fix the existing `findSimilarChunks` native query. Use `Arrays.toString(floats).replace(" ", "")` to match the exact requirement, though the provided hint says `Arrays.toString(floats).replace(", ", ",")` might be better or `[0.1, 0.2]`. The repository query uses `CAST(:embedding AS vector)`.

5. **AiKnowledgeService (App 03)**
   - Update `askQuestion()` to use `EmbeddingService` for question.
   - Use `findSimilarChunks`.
   - If AI gateway fails (embedding is null), fallback.
   - Construct prompt with context. Call `AiService.callLlmRaw()`. Parse and save answer.

6. **SearchController (App 03)**
   - Update `search()` endpoint to handle `type=semantic` logic.
   - Distinct map over chunks to return relevant `KbPage`s. If embedding fails, fallback to `type=keyword`.

7. **Verification**
   - Run tests for `saas-os-core` and App 03. Ensure no regressions.
