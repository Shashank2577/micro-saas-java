package com.changelog.invoice.controller;

import com.changelog.config.TenantResolver;
import com.changelog.invoice.domain.Invoice;
import com.changelog.invoice.domain.Payment;
import com.changelog.invoice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<Invoice>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(invoiceService.listInvoices(tenantResolver.getTenantId(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Invoice> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.getInvoice(id, tenantResolver.getTenantId(jwt)));
    }

    @GetMapping("/public/{token}")
    public ResponseEntity<Invoice> getPublic(@PathVariable String token) {
        return ResponseEntity.ok(invoiceService.getInvoiceByPublicToken(token));
    }

    @PostMapping
    public ResponseEntity<Invoice> create(@AuthenticationPrincipal Jwt jwt, @RequestBody Invoice invoice) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invoiceService.createInvoice(tenantResolver.getTenantId(jwt), invoice));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Invoice> updateStatus(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID id,
                                                @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                invoiceService.updateStatus(id, tenantResolver.getTenantId(jwt), body.get("status")));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<Invoice> recordPayment(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID id,
                                                 @RequestBody Payment payment) {
        return ResponseEntity.ok(
                invoiceService.recordPayment(id, tenantResolver.getTenantId(jwt), payment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        invoiceService.deleteInvoice(id, tenantResolver.getTenantId(jwt));
        return ResponseEntity.noContent().build();
    }
}
