package com.changelog.service;

import com.changelog.model.KbPage;
import com.changelog.model.PageVersion;
import com.changelog.model.PageChunk;
import com.changelog.repository.KbPageRepository;
import com.changelog.repository.PageVersionRepository;
import com.changelog.repository.PageChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KbPageService {

    private final KbPageRepository pageRepository;
    private final PageVersionRepository versionRepository;
    private final PageChunkRepository pageChunkRepository;
    private final EmbeddingService embeddingService;

    @Transactional(readOnly = true)
    public List<KbPage> getPagesInSpace(UUID tenantId, UUID spaceId) {
        return pageRepository.findBySpaceIdAndTenantIdOrderByPositionAsc(spaceId, tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<KbPage> getPage(UUID tenantId, UUID pageId) {
        return pageRepository.findByIdAndTenantId(pageId, tenantId);
    }

    @Transactional
    public KbPage createPage(UUID tenantId, KbPage page) {
        page.setTenantId(tenantId);
        KbPage saved = pageRepository.save(page);
        saveVersion(saved);
        indexPage(saved);
        return saved;
    }

    @Transactional
    public KbPage updatePage(UUID tenantId, UUID pageId, KbPage pageUpdates) {
        return pageRepository.findByIdAndTenantId(pageId, tenantId).map(existing -> {
            existing.setTitle(pageUpdates.getTitle());
            existing.setContent(pageUpdates.getContent());
            existing.setTags(pageUpdates.getTags());
            existing.setStatus("draft");
            KbPage saved = pageRepository.save(existing);
            saveVersion(saved);
            indexPage(saved);
            return saved;
        }).orElseThrow(() -> new IllegalArgumentException("Page not found"));
    }

    @Transactional
    public KbPage publishPage(UUID tenantId, UUID pageId) {
        return pageRepository.findByIdAndTenantId(pageId, tenantId).map(page -> {
            page.setStatus("published");
            return pageRepository.save(page);
        }).orElseThrow(() -> new IllegalArgumentException("Page not found"));
    }

    @Transactional
    public void deletePage(UUID tenantId, UUID pageId) {
        pageRepository.findByIdAndTenantId(pageId, tenantId).ifPresent(pageRepository::delete);
    }

    private void saveVersion(KbPage page) {
        List<PageVersion> versions = versionRepository.findByPageIdOrderByVersionNumDesc(page.getId());
        int nextVersion = versions.isEmpty() ? 1 : versions.get(0).getVersionNum() + 1;

        PageVersion newVersion = PageVersion.builder()
                .page(page)
                .versionNum(nextVersion)
                .content(page.getContent())
                .title(page.getTitle())
                .editedBy(page.getOwnerId())
                .build();
        versionRepository.save(newVersion);
    }

    private void indexPage(KbPage page) {
        // 1. Delete existing chunks for this page
        pageChunkRepository.deleteByPageId(page.getId());

        if (page.getContent() == null || page.getContent().isBlank()) {
            return;
        }

        // 2. Split content into ~500-word chunks with 50-word overlap
        List<String> chunks = chunkText(page.getContent(), 500, 50);

        // 3. For each chunk: generate embedding and save PageChunk
        for (int i = 0; i < chunks.size(); i++) {
            float[] embedding = embeddingService.generateEmbedding(chunks.get(i));
            PageChunk chunk = PageChunk.builder()
                .page(page)
                .chunkIndex(i)
                .content(chunks.get(i))
                .embedding(embeddingService.toPGvector(embedding))
                .build();
            pageChunkRepository.save(chunk);
        }
    }

    private List<String> chunkText(String text, int maxWords, int overlapWords) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.split("\\s+");

        int i = 0;
        while (i < words.length) {
            int end = Math.min(i + maxWords, words.length);
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < end; j++) {
                sb.append(words[j]);
                if (j < end - 1) {
                    sb.append(" ");
                }
            }
            chunks.add(sb.toString());

            if (end == words.length) {
                break;
            }
            i += (maxWords - overlapWords);
            if (i >= words.length) {
                break;
            }
            // Ensure progress in case maxWords <= overlapWords
            if (maxWords - overlapWords <= 0) {
                i = end;
            }
        }
        return chunks;
    }
}