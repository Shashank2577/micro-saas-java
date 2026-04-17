package com.changelog.business.success.controller;

import com.changelog.business.success.dto.CreateSupportTicketRequest;
import com.changelog.business.success.model.SupportTicket;
import com.changelog.business.success.service.SupportTicketService;
import com.changelog.config.TenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support-tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<SupportTicket>> getAllTickets(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(supportTicketService.getAll(tenantId));
    }

    @PostMapping
    public ResponseEntity<SupportTicket> createTicket(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateSupportTicketRequest request) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(supportTicketService.create(tenantId, request));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<SupportTicket> getTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ticketId) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        return supportTicketService.getById(tenantId, ticketId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{ticketId}/status")
    public ResponseEntity<SupportTicket> updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ticketId,
            @RequestBody Map<String, String> body) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        String status = body.get("status");
        return supportTicketService.updateStatus(tenantId, ticketId, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{ticketId}/close")
    public ResponseEntity<SupportTicket> closeTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ticketId) {

        UUID tenantId = tenantResolver.getTenantId(jwt);
        return supportTicketService.close(tenantId, ticketId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
