package com.changelog.business.monetization;

import com.changelog.business.monetization.model.StripeProduct;
import com.changelog.business.monetization.model.StripeSubscription;
import com.changelog.business.monetization.repository.StripeProductRepository;
import com.changelog.business.monetization.repository.StripeSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StripeIntegrationTest {

    @Autowired
    private StripeProductRepository productRepository;

    @Autowired
    private StripeSubscriptionRepository subscriptionRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID customerId;
    private StripeProduct testProduct;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        jdbcTemplate.update("INSERT INTO cc.tenants (id, name, slug, plan_tier) VALUES (?, 'Monetization Test Tenant', ?, 'enterprise')", tenantId, "stripe-test-tenant-" + tenantId);
        jdbcTemplate.update("INSERT INTO cc.users (id, tenant_id, email, name, role) VALUES (?, ?, ?, 'Test User', 'admin')", userId, tenantId, "user-" + userId + "@example.com");
        jdbcTemplate.update("INSERT INTO stripe_customers (id, tenant_id, user_id, stripe_id, email) VALUES (?, ?, ?, ?, ?)", customerId, tenantId, userId, "cus_test_" + customerId, "test@example.com");

        subscriptionRepository.deleteAll();
        productRepository.deleteAll();

        testProduct = StripeProduct.builder()
            .tenantId(tenantId)
            .stripeId("prod_test_pro")
            .name("Professional Plan")
            .description("Full-featured plan for growing teams")
            .priceCents(9900)
            .currency("usd")
            .interval("month")
            .intervalCount(1)
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        
        testProduct = productRepository.save(testProduct);
    }

    @Test
    void testCreateStripeProduct() {
        List<String> features = Arrays.asList(
            "Unlimited projects",
            "Full API access",
            "24/7 support",
            "Custom domain"
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tier", "professional");
        metadata.put("max_team_members", 50);
        metadata.put("feature_flags", Map.of("ai_enabled", true, "beta_access", true));

        StripeProduct product = StripeProduct.builder()
            .tenantId(tenantId)
            .stripeId("prod_test_pro_method")
            .name("Professional Plan")
            .description("Full-featured plan for growing teams")
            .priceCents(9900) // $99.00
            .currency("usd")
            .interval("month")
            .intervalCount(1)
            .features(features)
            .metadata(metadata)
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        StripeProduct saved = productRepository.save(product);

        assertNotNull(saved.getId());
        assertEquals("Professional Plan", saved.getName());
        assertEquals(4, saved.getFeatures().size());
        assertEquals("professional", saved.getMetadata().get("tier"));
        assertTrue(saved.isRecurring());
        assertEquals("$99.00/month", saved.getFormattedPrice());
    }

    @Test
    void testCreateStripeSubscription() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("company", "Acme Inc");
        metadata.put("department", "Engineering");
        metadata.put("cost_center", "CC-12345");

        StripeSubscription subscription = StripeSubscription.builder()
            .tenantId(tenantId)
            .customerId(customerId)
            .stripeId("sub_test_123")
            .stripeProductId(testProduct.getId())
            .status("active")
            .currentPeriodStart(LocalDateTime.now())
            .currentPeriodEnd(LocalDateTime.now().plusMonths(1))
            .quantity(10)
            .metadata(metadata)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        StripeSubscription saved = subscriptionRepository.save(subscription);

        assertNotNull(saved.getId());
        assertTrue(saved.isActive());
        assertFalse(saved.isCanceled());
        assertEquals("Acme Inc", saved.getMetadata().get("company"));
    }

    @Test
    void testSubscriptionTrialStatus() {
        LocalDateTime now = LocalDateTime.now();

        StripeSubscription subscription = StripeSubscription.builder()
            .tenantId(tenantId)
            .customerId(customerId)
            .stripeId("sub_trial_456")
            .stripeProductId(testProduct.getId())
            .status("trialing")
            .trialStart(now.minusDays(5))
            .trialEnd(now.plusDays(9))
            .createdAt(now)
            .updatedAt(now)
            .build();

        StripeSubscription saved = subscriptionRepository.save(subscription);

        assertTrue(saved.isInTrial());
        assertTrue(saved.isActive());
    }

    @Test
    void testSubscriptionCancellation() {
        LocalDateTime now = LocalDateTime.now();

        StripeSubscription subscription = StripeSubscription.builder()
            .tenantId(tenantId)
            .customerId(customerId)
            .stripeId("sub_cancel_789")
            .stripeProductId(testProduct.getId())
            .status("active")
            .cancelAtPeriodEnd(true)
            .currentPeriodEnd(now.plusDays(14))
            .createdAt(now)
            .updatedAt(now)
            .build();

        StripeSubscription saved = subscriptionRepository.save(subscription);

        assertTrue(saved.willCancelAtPeriodEnd());
        assertFalse(saved.isCanceled());

        // Simulate cancellation on period end
        subscription.setStatus("canceled");
        subscription.setCanceledAt(now.plusDays(14));
        StripeSubscription updated = subscriptionRepository.save(subscription);

        assertTrue(updated.isCanceled());
    }
}