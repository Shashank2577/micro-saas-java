package com.changelog.service;

import com.changelog.model.KbPage;
import com.changelog.model.PageVersion;
import com.changelog.repository.KbPageRepository;
import com.changelog.repository.PageVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KbPageService {

    private final KbPageRepository pageRepository;
    private final PageVersionRepository versionRepository;

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
}