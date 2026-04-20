package com.changelog.service;

import com.changelog.model.AiQaSession;
import com.changelog.model.PageChunk;
import com.changelog.repository.AiQaSessionRepository;
import com.changelog.repository.KbPageRepository;
import com.changelog.repository.PageChunkRepository;
import com.changelog.repository.SearchQueryLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiKnowledgeService {

    private final AiQaSessionRepository qaSessionRepository;
    private final PageChunkRepository chunkRepository;
    private final SearchQueryLogRepository searchQueryLogRepository;

    @Transactional
    public AiQaSession askQuestion(UUID tenantId, UUID userId, String question) {
        // Mocking RAG pipeline for phase 1 implementation
        // 1. Generate embedding (Mocked)
        String mockEmbedding = "[0.1, 0.2, 0.3]"; // Would come from AI gateway

        // 2. Fetch similar chunks
        List<PageChunk> similarChunks = chunkRepository.findSimilarChunks(tenantId, mockEmbedding, 8);

        // 3. Generate answer (Mocked)
        String answer = "This is a mocked AI response based on the context.";
        String citations = "[{\"page_id\":\"123\", \"title\":\"Mock Page\", \"excerpt\":\"mock context\"}]";

        // 4. Save session
        AiQaSession session = AiQaSession.builder()
                .tenantId(tenantId)
                .userId(userId)
                .question(question)
                .answer(answer)
                .citations(citations)
                .build();
        return qaSessionRepository.save(session);
    }

    @Transactional
    public void submitFeedback(UUID sessionId, String feedback) {
        qaSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setFeedback(feedback);
            qaSessionRepository.save(session);
        });
    }

    @Transactional(readOnly = true)
    public List<String> getGapReport(UUID tenantId) {
        return searchQueryLogRepository.findTopGaps(tenantId);
    }
}