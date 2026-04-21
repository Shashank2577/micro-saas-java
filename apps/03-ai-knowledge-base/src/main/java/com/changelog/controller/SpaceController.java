package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.model.Space;
import com.changelog.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<Space>> getSpaces(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(spaceService.getSpaces(tenantResolver.getTenantId(jwt)));
    }

    @PostMapping
    public ResponseEntity<Space> createSpace(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Space space) {
        return ResponseEntity.ok(spaceService.createSpace(tenantResolver.getTenantId(jwt), space));
    }

    @GetMapping("/{spaceId}")
    public ResponseEntity<Space> getSpace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID spaceId) {
        return spaceService.getSpace(tenantResolver.getTenantId(jwt), spaceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
