package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.model.KbPage;
import com.changelog.service.KbPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class KbPageController {

    private final KbPageService kbPageService;
    private final TenantResolver tenantResolver;

    @GetMapping("/spaces/{spaceId}/pages")
    public ResponseEntity<List<KbPage>> getPages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spaceId) {
        return ResponseEntity.ok(kbPageService.getPagesInSpace(tenantResolver.getTenantId(jwt), spaceId));
    }

    @PostMapping("/spaces/{spaceId}/pages")
    public ResponseEntity<KbPage> createPage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spaceId,
            @RequestBody KbPage page) {
        // Assume Space entity lookup would happen here in a real scenario
        // For now, mapping ID manually is fine.
        return ResponseEntity.ok(kbPageService.createPage(tenantResolver.getTenantId(jwt), page));
    }

    @GetMapping("/pages/{pageId}")
    public ResponseEntity<KbPage> getPage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pageId) {
        return kbPageService.getPage(tenantResolver.getTenantId(jwt), pageId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/pages/{pageId}")
    public ResponseEntity<KbPage> updatePage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pageId,
            @RequestBody KbPage pageUpdates) {
        return ResponseEntity.ok(kbPageService.updatePage(tenantResolver.getTenantId(jwt), pageId, pageUpdates));
    }

    @PutMapping("/pages/{pageId}/publish")
    public ResponseEntity<KbPage> publishPage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pageId) {
        return ResponseEntity.ok(kbPageService.publishPage(tenantResolver.getTenantId(jwt), pageId));
    }

    @DeleteMapping("/pages/{pageId}")
    public ResponseEntity<Void> deletePage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pageId) {
        kbPageService.deletePage(tenantResolver.getTenantId(jwt), pageId);
        return ResponseEntity.noContent().build();
    }
}
