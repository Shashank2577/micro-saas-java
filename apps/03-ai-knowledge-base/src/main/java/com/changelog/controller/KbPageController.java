package com.changelog.controller;

import com.changelog.model.KbPage;
import com.changelog.service.KbPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class KbPageController {

    private final KbPageService kbPageService;

    @GetMapping("/spaces/{spaceId}/pages")
    public ResponseEntity<List<KbPage>> getPages(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID spaceId) {
        return ResponseEntity.ok(kbPageService.getPagesInSpace(tenantId, spaceId));
    }

    @PostMapping("/spaces/{spaceId}/pages")
    public ResponseEntity<KbPage> createPage(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID spaceId,
            @RequestBody KbPage page) {
        // Assume Space entity lookup would happen here in a real scenario
        // For now, mapping ID manually is fine.
        return ResponseEntity.ok(kbPageService.createPage(tenantId, page));
    }

    @GetMapping("/pages/{pageId}")
    public ResponseEntity<KbPage> getPage(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID pageId) {
        return kbPageService.getPage(tenantId, pageId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/pages/{pageId}")
    public ResponseEntity<KbPage> updatePage(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID pageId,
            @RequestBody KbPage pageUpdates) {
        return ResponseEntity.ok(kbPageService.updatePage(tenantId, pageId, pageUpdates));
    }

    @PutMapping("/pages/{pageId}/publish")
    public ResponseEntity<KbPage> publishPage(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID pageId) {
        return ResponseEntity.ok(kbPageService.publishPage(tenantId, pageId));
    }

    @DeleteMapping("/pages/{pageId}")
    public ResponseEntity<Void> deletePage(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID pageId) {
        kbPageService.deletePage(tenantId, pageId);
        return ResponseEntity.noContent().build();
    }
}