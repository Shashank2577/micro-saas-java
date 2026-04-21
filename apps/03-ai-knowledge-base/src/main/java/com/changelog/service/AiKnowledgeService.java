package com.changelog.service;

import com.changelog.ai.AiService;
import com.changelog.model.AiQaSession;
import com.changelog.model.KbPage;
import com.changelog.model.PageChunk;
import com.changelog.repository.AiQaSessionRepository;
import com.changelog.repository.KbPageRepository;
import com.changelog.repository.PageChunkRepository;
import com.changelog.repository.SearchQueryLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiKnowledgeService {

    private final AiQaSessionRepository qaSessionRepository;
    private final PageChunkRepository chunkRepository;
    private final SearchQueryLogRepository searchQueryLogRepository;
    private final KbPageRepository pageRepository;
    private final EmbeddingService embeddingService;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public AiQaSession askQuestion(UUID tenantId, UUID userId, String question) {
        float[] questionEmbedding = embeddingService.generateEmbedding(question);

        List<PageChunk> relevantChunks;
        String answer;
        String citationsJson = "[]";

        if (questionEmbedding == null) {
            log.warn("AI unavailable for embedding, falling back to keyword search for question: {}", question);
            List<KbPage> fallbackPages = pageRepository.searchByKeyword(tenantId, question);
            if (fallbackPages.isEmpty()) {
                answer = "I don't have information about that in this knowledge base. (AI unavailable)";
            } else {
                answer = "AI unavailable. Showing results from keyword search instead.";
                citationsJson = buildCitationsFromPages(fallbackPages);
            }
        } else {
            String embeddingStr = Arrays.toString(questionEmbedding);
            relevantChunks = chunkRepository.findSimilarChunks(tenantId, embeddingStr, 5);

            String context = relevantChunks.stream()
                    .map(PageChunk::getContent)
                    .collect(Collectors.joining("\n\n---\n\n"));

            String prompt = String.format("""
                You are a helpful assistant. Answer the question based ONLY on the context provided below.
                If the answer is not in the context, say "I don't have information about that in this knowledge base."

                Context:
                %s

                Question: %s

                Answer:
                """, context, question);

            try {
                answer = aiService.callLlmRaw(prompt);
                citationsJson = buildCitationsFromChunks(relevantChunks);
            } catch (Exception e) {
                log.error("AI gateway failed for completion: {}", e.getMessage(), e);
                answer = "I don't have information about that in this knowledge base. (AI unavailable)";
            }
        }

        AiQaSession session = AiQaSession.builder()
                .tenantId(tenantId)
                .userId(userId)
                .question(question)
                .answer(answer)
                .citations(citationsJson)
                .build();
        return qaSessionRepository.save(session);
    }

    private String buildCitationsFromChunks(List<PageChunk> chunks) {
        List<Map<String, String>> citations = chunks.stream().map(c -> {
            Map<String, String> map = new HashMap<>();
            map.put("page_id", c.getPage().getId().toString());
            map.put("title", c.getPage().getTitle());
            map.put("excerpt", c.getContent().length() > 100 ? c.getContent().substring(0, 100) + "..." : c.getContent());
            return map;
        }).collect(Collectors.toList());

        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String buildCitationsFromPages(List<KbPage> pages) {
        List<Map<String, String>> citations = pages.stream().limit(5).map(p -> {
            Map<String, String> map = new HashMap<>();
            map.put("page_id", p.getId().toString());
            map.put("title", p.getTitle());
            map.put("excerpt", p.getContent().length() > 100 ? p.getContent().substring(0, 100) + "..." : p.getContent());
            return map;
        }).collect(Collectors.toList());

        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            return "[]";
        }
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