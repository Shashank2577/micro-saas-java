package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.model.KbPage;
import com.changelog.repository.KbPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final KbPageRepository pageRepository;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<KbPage>> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String q,
            @RequestParam(defaultValue = "keyword") String type) {
        
        UUID tenantId = tenantResolver.getTenantId(jwt);

        if ("keyword".equalsIgnoreCase(type)) {
            return ResponseEntity.ok(pageRepository.searchByKeyword(tenantId, q));
        }
        
        // In a real semantic search, we would hit the AI gateway, get an embedding, 
        // and do vector search. Using keyword for MVP placeholder.
        return ResponseEntity.ok(pageRepository.searchByKeyword(tenantId, q));
    }
}
