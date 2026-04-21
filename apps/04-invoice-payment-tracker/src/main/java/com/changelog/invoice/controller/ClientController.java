package com.changelog.invoice.controller;

import com.changelog.config.TenantResolver;
import com.changelog.invoice.domain.Client;
import com.changelog.invoice.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<Client>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(clientService.listClients(tenantResolver.getTenantId(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Client> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.getClient(id, tenantResolver.getTenantId(jwt)));
    }

    @PostMapping
    public ResponseEntity<Client> create(@AuthenticationPrincipal Jwt jwt, @RequestBody Client client) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clientService.createClient(tenantResolver.getTenantId(jwt), client));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Client> update(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID id,
                                         @RequestBody Client updates) {
        return ResponseEntity.ok(clientService.updateClient(id, tenantResolver.getTenantId(jwt), updates));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        clientService.deleteClient(id, tenantResolver.getTenantId(jwt));
        return ResponseEntity.noContent().build();
    }
}
