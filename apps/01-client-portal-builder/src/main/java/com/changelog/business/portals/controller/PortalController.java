package com.changelog.business.portals.controller;

import com.changelog.business.portals.dto.CreatePortalRequest;
import com.changelog.business.portals.dto.PortalResponse;
import com.changelog.business.portals.model.Portal;
import com.changelog.business.portals.service.PortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portals")
@RequiredArgsConstructor
public class PortalController {

    private final PortalService portalService;
    // For now, we will extract tenantId manually if TenantResolver is not available in the dependency.
    // In a real scenario, this would be injected or extracted via a shared lib.
    
    private UUID getTenantId(JwtAuthenticationToken jwt) {
        if (jwt == null || jwt.getToken() == null) {
            // Fallback for local development if needed, or throw exception
            return UUID.fromString("550e8400-e29b-41d4-a716-446655440000"); // demo tenant
        }
        String tenantIdStr = jwt.getToken().getClaimAsString("tenant_id");
        if (tenantIdStr != null) {
            return UUID.fromString(tenantIdStr);
        }
        return UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    }

    @GetMapping
    public ResponseEntity<List<PortalResponse>> listPortals(JwtAuthenticationToken jwt) {
        UUID tenantId = getTenantId(jwt);
        List<PortalResponse> portals = portalService.getPortals(tenantId)
                .stream()
                .map(PortalResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(portals);
    }

    @PostMapping
    public ResponseEntity<PortalResponse> createPortal(@Valid @RequestBody CreatePortalRequest request, JwtAuthenticationToken jwt) {
        UUID tenantId = getTenantId(jwt);
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
    public ResponseEntity<PortalResponse> getPortal(@PathVariable UUID id, JwtAuthenticationToken jwt) {
        UUID tenantId = getTenantId(jwt);
        return portalService.getPortal(id, tenantId)
                .map(portal -> ResponseEntity.ok(PortalResponse.fromEntity(portal)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archivePortal(@PathVariable UUID id, JwtAuthenticationToken jwt) {
        UUID tenantId = getTenantId(jwt);
        portalService.archivePortal(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
