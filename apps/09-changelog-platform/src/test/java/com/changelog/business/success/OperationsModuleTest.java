package com.changelog.business.success;

import com.changelog.business.success.model.CustomerHealthScore;
import com.changelog.business.success.model.SupportTicket;
import com.changelog.business.success.repository.CustomerHealthScoreRepository;
import com.changelog.business.success.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OperationsModuleTest {

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Autowired
    private CustomerHealthScoreRepository healthScoreRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID customerId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        userId = UUID.randomUUID();
        
        jdbcTemplate.update("INSERT INTO cc.tenants (id, name, slug, plan_tier) VALUES (?, 'Ops Test Tenant', ?, 'startup')", tenantId, "ops-test-tenant-" + tenantId);
        jdbcTemplate.update("INSERT INTO cc.users (id, tenant_id, email, name, role) VALUES (?, ?, ?, 'Ops User', 'admin')", userId, tenantId, "ops-user-" + userId + "@example.com");
        
        ticketRepository.deleteAll();
        healthScoreRepository.deleteAll();
    }

    @Test
    void testCreateSupportTicket() {
        SupportTicket ticket = SupportTicket.builder()
            .tenantId(tenantId)
            .customerId(customerId)
            .subject("API Rate Limiting Issue")
            .description("Customer experiencing 429 errors on production API")
            .status("open")
            .priority("high")
            .category("technical")
            .assignedTo(userId)
            .channel("email")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        SupportTicket saved = ticketRepository.save(ticket);

        assertNotNull(saved.getId());
        assertEquals("open", saved.getStatus());
        assertEquals("high", saved.getPriority());
    }

    @Test
    void testCreateCustomerHealthScore() {
        List<CustomerHealthScore.HealthSignal> signals = Arrays.asList(
            new CustomerHealthScore.HealthSignal("usage_trend", "increasing", 10, "Usage is up 10%"),
            new CustomerHealthScore.HealthSignal("api_errors", 12, -5, "High error rate")
        );

        CustomerHealthScore health = CustomerHealthScore.builder()
            .tenantId(tenantId)
            .customerId(customerId)
            .score(82)
            .signals(signals)
            .riskLevel("low")
            .calculatedAt(LocalDateTime.now())
            .build();

        CustomerHealthScore saved = healthScoreRepository.save(health);

        assertNotNull(saved.getId());
        assertEquals(82, saved.getScore());
        assertEquals("low", saved.getRiskLevel());
        assertEquals("usage_trend", saved.getSignals().get(0).getType());
    }

    @Test
    void testHealthScoreRiskAssessment() {
        List<CustomerHealthScore.HealthSignal> riskSignals = Arrays.asList(
            new CustomerHealthScore.HealthSignal("churn_probability", 0.75, -20, "High churn risk"),
            new CustomerHealthScore.HealthSignal("payment_issues", true, -15, "Payment failed")
        );

        CustomerHealthScore health = CustomerHealthScore.builder()
            .tenantId(tenantId)
            .customerId(customerId)
            .score(28)
            .signals(riskSignals)
            .riskLevel("critical")
            .calculatedAt(LocalDateTime.now())
            .build();

        CustomerHealthScore saved = healthScoreRepository.save(health);

        assertEquals("critical", saved.getRiskLevel());
        assertEquals("churn_probability", saved.getSignals().get(0).getType());
    }
}