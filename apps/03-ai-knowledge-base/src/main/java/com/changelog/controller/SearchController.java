package com.changelog.controller;

import com.changelog.model.KbPage;
import com.changelog.model.PageChunk;
import com.changelog.repository.KbPageRepository;
import com.changelog.repository.PageChunkRepository;
import com.changelog.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final KbPageRepository pageRepository;
    private final PageChunkRepository pageChunkRepository;
    private final EmbeddingService embeddingService;

    @GetMapping
    public ResponseEntity<List<KbPage>> search(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam String q,
            @RequestParam(defaultValue = "keyword") String type) {
        
        if ("semantic".equalsIgnoreCase(type)) {
            float[] queryEmbedding = embeddingService.generateEmbedding(q);
            if (queryEmbedding != null) {
                String embStr = Arrays.toString(queryEmbedding);
                List<PageChunk> chunks = pageChunkRepository.findSimilarChunks(tenantId, embStr, 10);
                List<KbPage> distinctPages = chunks.stream()
                        .map(PageChunk::getPage)
                        .distinct()
                        .collect(Collectors.toList());
                return ResponseEntity.ok(distinctPages);
            }
        }
        
        // Default / Fallback to keyword search
        return ResponseEntity.ok(pageRepository.searchByKeyword(tenantId, q));
    }
}