package com.changelog.invoice;

import com.changelog.invoice.domain.Client;
import com.changelog.invoice.domain.Invoice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    private UUID tenantId;
    private UUID clientId;
    private UUID invoiceId;
    private String publicToken;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        publicToken = "token-" + UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO cc.tenants (id, name, slug, plan_tier) VALUES (?, 'Test Tenant', ?, 'startup')",
                tenantId, "test-tenant-" + tenantId);

        jdbcTemplate.update("INSERT INTO clients (id, tenant_id, name, email, currency) VALUES (?, ?, 'Test Client', 'client@example.com', 'USD')",
                clientId, tenantId);

        jdbcTemplate.update("INSERT INTO invoices (id, tenant_id, client_id, invoice_number, status, total, public_token) VALUES (?, ?, ?, 'INV-001', 'draft', 100.00, ?)",
                invoiceId, tenantId, clientId, publicToken);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM invoice_line_items WHERE invoice_id = ?", invoiceId);
        jdbcTemplate.update("DELETE FROM payments WHERE invoice_id = ?", invoiceId);
        jdbcTemplate.update("DELETE FROM invoices WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM clients WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM cc.tenants WHERE id = ?", tenantId);
    }

    @Test
    void testListInvoices_returnsCurrentTenantInvoices() throws Exception {
        mockMvc.perform(get("/api/invoices")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].invoiceNumber").value("INV-001"));
    }

    @Test
    void testCreateInvoice_persistsCorrectly() throws Exception {
        Invoice newInvoice = new Invoice();
        Client client = new Client();
        client.setId(clientId);
        newInvoice.setClient(client);
        newInvoice.setInvoiceNumber("INV-002");
        newInvoice.setStatus("draft");
        newInvoice.setCurrency("USD");

        mockMvc.perform(post("/api/invoices")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newInvoice)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.invoiceNumber").value("INV-002"))
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(jsonPath("$.tenant.id").value(tenantId.toString()));
    }

    @Test
    void testUpdateInvoiceStatus_changesStatusToSent() throws Exception {
        mockMvc.perform(patch("/api/invoices/" + invoiceId + "/status")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"sent\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("sent"));
    }

    @Test
    void testMarkInvoicePaid_changesStatusToPaid() throws Exception {
        mockMvc.perform(patch("/api/invoices/" + invoiceId + "/status")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"paid\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("paid"));
    }

    @Test
    void testGetInvoiceByPublicToken_returnsInvoiceWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/invoices/public/" + publicToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invoiceNumber").value("INV-001"));
    }

    @Test
    void testGetInvoice_returns404ForOtherTenantInvoice() throws Exception {
        UUID otherTenantId = UUID.randomUUID();
        mockMvc.perform(get("/api/invoices/" + invoiceId)
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", otherTenantId.toString()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void testCreateInvoice_returns400WhenClientIdMissing() throws Exception {
        Invoice invalidInvoice = new Invoice();
        invalidInvoice.setInvoiceNumber("INV-ERR");

        mockMvc.perform(post("/api/invoices")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidInvoice)))
            .andExpect(status().isBadRequest());
    }
}
