package com.changelog.business.portals.controller;

import com.changelog.business.portals.dto.CreatePortalRequest;
import com.changelog.business.portals.dto.PortalResponse;
import com.changelog.business.portals.model.Portal;
import com.changelog.business.portals.service.PortalService;
import com.changelog.config.TenantResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portals")
@RequiredArgsConstructor
public class PortalController {

    private final PortalService portalService;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<PortalResponse>> listPortals(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        List<PortalResponse> portals = portalService.getPortals(tenantId)
                .stream()
                .map(PortalResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(portals);
    }

    @PostMapping
    public ResponseEntity<PortalResponse> createPortal(@Valid @RequestBody CreatePortalRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        Portal portal = new Portal();
        portal.setTenantId(tenantId);
        portal.setName(request.getName());
        portal.setSlug(request.getSlug());
        portal.setClientName(request.getClientName());
        if (request.getBranding() != null) {
            portal.setBranding(request.getBranding());
        }

        Portal saved = portalService.createPortal(portal);
        return ResponseEntity.ok(PortalResponse.fromEntity(saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PortalResponse> getPortal(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return portalService.getPortal(id, tenantId)
                .map(portal -> ResponseEntity.ok(PortalResponse.fromEntity(portal)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archivePortal(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        portalService.archivePortal(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
