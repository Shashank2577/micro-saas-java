# WO-004: Implement Real AI in App 03

## Summary
The mock AI in App 03 has been replaced with a real RAG (Retrieval-Augmented Generation) pipeline.

- `saas-os-core` was extended with `EmbeddingRequest`, `EmbeddingResponse`, an `@POST("embeddings")` Retrofit route in `LiteLlmApi`, and a public `callLlmRaw` method in `AiService`.
- `EmbeddingService` was built in App 03 to request text embeddings from the LiteLLM gateway, gracefully returning `null` on failure.
- `KbPageService` was updated to chunk `KbPage` content into ~500 words with a 50-word overlap, embed those chunks, and save them as `PageChunk` entities containing `pgvector` data.
- `SearchController` handles `type=semantic` queries, finding top chunks and extracting unique associated `KbPage` results, falling back to keyword search if AI is unreachable.
- `AiKnowledgeService` resolves user questions using top-5 relevant semantic chunks, assembling a context-rich prompt for the LLM to process. Fallbacks to keyword context are invoked if embeddings fail.

## Notes
- Build succeeds.
- Graceful degradation ensures the application functions smoothly even if the external LLM is offline.
