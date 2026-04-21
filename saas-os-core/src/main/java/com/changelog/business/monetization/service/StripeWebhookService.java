package com.changelog.business.monetization.service;

import com.changelog.business.monetization.dto.StripeWebhookEvent;
import com.changelog.business.monetization.model.StripeSubscription;
import com.changelog.business.monetization.repository.StripeProductRepository;
import com.changelog.business.monetization.repository.StripeSubscriptionRepository;
import com.changelog.business.orchestration.event.BusinessEvent;
import com.changelog.business.orchestration.service.BusinessEventPublisher;
import com.changelog.business.retention.service.DripCampaignService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeWebhookService {

    private final StripeProductRepository productRepository;
    private final StripeSubscriptionRepository subscriptionRepository;
    private final BusinessEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final DripCampaignService dripCampaignService;

    /**
     * Handle Stripe webhook event
     */
    @Transactional
    public void handleWebhook(StripeWebhookEvent event, String stripeId) {
        log.info("Processing Stripe webhook: type={}, id={}", event.getType(), event.getId());

        try {
            switch (event.getType()) {
                case "customer.subscription.created":
                case "customer.subscription.updated":
                    handleSubscriptionEvent(event);
                    break;

                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(event);
                    break;

                case "invoice.payment_succeeded":
                    handlePaymentSucceeded(event);
                    break;

                case "invoice.payment_failed":
                    handlePaymentFailed(event);
                    break;

                case "invoice.paid":
                    handleInvoicePaid(event);
                    break;

                default:
                    log.debug("Unhandled webhook type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Error processing webhook: type={}, error={}", event.getType(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Handle subscription created/updated
     */
    private void handleSubscriptionEvent(StripeWebhookEvent event) {
        Map<String, Object> subscriptionData = event.getData().getObject().getAttributes();

        String stripeSubscriptionId = (String) subscriptionData.get("id");

        // Extract metadata
        Map<String, String> metadata = (Map<String, String>) subscriptionData.get("metadata");
        if (metadata == null || !metadata.containsKey("tenant_id") || !metadata.containsKey("customer_id")) {
            log.error("Missing required metadata (tenant_id or customer_id) for subscription: {}", stripeSubscriptionId);
            return;
        }

        UUID tenantId = UUID.fromString(metadata.get("tenant_id"));
        UUID customerId = UUID.fromString(metadata.get("customer_id"));

        StripeSubscription subscription = subscriptionRepository.findByStripeId(stripeSubscriptionId)
                .orElseGet(() -> createNewSubscription(stripeSubscriptionId, tenantId, customerId));

        // Update subscription fields
        updateSubscriptionFromStripe(subscription, subscriptionData);

        subscription = subscriptionRepository.save(subscription);

        // Publish business event
        eventPublisher.publish(
                "customer.subscription.created".equals(event.getType())
                        ? BusinessEvent.BusinessEventType.SUBSCRIPTION_CREATED
                        : BusinessEvent.BusinessEventType.SUBSCRIPTION_UPDATED,
                tenantId,
                customerId,
                Map.of(
                        "subscriptionId", subscription.getId(),
                        "productId", subscription.getStripeProductId(),
                        "status", subscription.getStatus()
                )
        );

        // Trigger drip campaign enrollment on subscription created
        if ("customer.subscription.created".equals(event.getType())) {
            dripCampaignService.processEnrollmentTrigger(tenantId, "subscription_created", customerId, "customer@example.com");
        }
    }

    /**
     * Handle subscription deleted/canceled
     */
    private void handleSubscriptionDeleted(StripeWebhookEvent event) {
        Map<String, Object> subscriptionData = event.getData().getObject().getAttributes();
        String stripeSubscriptionId = (String) subscriptionData.get("id");

        subscriptionRepository.findByStripeId(stripeSubscriptionId).ifPresent(subscription -> {
            subscription.setStatus("canceled");
            subscription.setCanceledAt(LocalDateTime.now());

            subscriptionRepository.save(subscription);

            // Publish business event
            eventPublisher.publish(
                    BusinessEvent.BusinessEventType.SUBSCRIPTION_CANCELED,
                    subscription.getTenantId(),
                    null, // TODO: Get customer ID
                    Map.of(
                            "subscriptionId", subscription.getId(),
                            "reason", "canceled_via_stripe"
                    )
            );

            // Trigger drip campaign enrollment on subscription canceled
            dripCampaignService.processEnrollmentTrigger(
                    subscription.getTenantId(), "subscription_canceled",
                    subscription.getCustomerId(), "customer@example.com");
        });
    }

    /**
     * Handle successful payment
     */
    private void handlePaymentSucceeded(StripeWebhookEvent event) {
        Map<String, Object> invoiceData = event.getData().getObject().getAttributes();

        // Extract subscription info
        String stripeSubscriptionId = (String) invoiceData.get("subscription");
        Long amountPaid = ((Number) invoiceData.get("amount_paid")).longValue();

        subscriptionRepository.findByStripeId(stripeSubscriptionId).ifPresent(subscription -> {
            // Publish payment succeeded event
            eventPublisher.publish(
                    BusinessEvent.BusinessEventType.PAYMENT_SUCCEEDED,
                    subscription.getTenantId(),
                    null,
                    Map.of(
                            "subscriptionId", subscription.getId(),
                            "amount", amountPaid,
                            "currency", invoiceData.get("currency")
                    )
            );
        });
    }

    /**
     * Handle failed payment
     */
    private void handlePaymentFailed(StripeWebhookEvent event) {
        Map<String, Object> invoiceData = event.getData().getObject().getAttributes();

        String stripeSubscriptionId = (String) invoiceData.get("subscription");

        subscriptionRepository.findByStripeId(stripeSubscriptionId).ifPresent(subscription -> {
            // Publish payment failed event (triggers dunning)
            eventPublisher.publish(
                    BusinessEvent.BusinessEventType.PAYMENT_FAILED,
                    subscription.getTenantId(),
                    null,
                    Map.of(
                            "subscriptionId", subscription.getId(),
                            "attemptCount", invoiceData.get("attempt_count"),
                            "nextPaymentAttempt", invoiceData.get("next_payment_attempt")
                    )
            );
        });
    }

    /**
     * Handle invoice paid (after dunning recovery)
     */
    private void handleInvoicePaid(StripeWebhookEvent event) {
        Map<String, Object> invoiceData = event.getData().getObject().getAttributes();
        String stripeSubscriptionId = (String) invoiceData.get("subscription");

        subscriptionRepository.findByStripeId(stripeSubscriptionId).ifPresent(subscription -> {
            // Publish dunning recovered event
            eventPublisher.publish(
                    BusinessEvent.BusinessEventType.DUNNING_RECOVERED,
                    subscription.getTenantId(),
                    null,
                    Map.of("subscriptionId", subscription.getId())
            );
        });
    }

    /**
     * Create new subscription from Stripe webhook
     */
    private StripeSubscription createNewSubscription(String stripeId, UUID tenantId, UUID customerId) {
        return StripeSubscription.builder()
                .stripeId(stripeId)
                .tenantId(tenantId)
                .customerId(customerId)
                .status("incomplete")
                .quantity(1)
                .build();
    }

    /**
     * Update subscription fields from Stripe data
     */
    private void updateSubscriptionFromStripe(StripeSubscription subscription, Map<String, Object> stripeData) {
        subscription.setStatus((String) stripeData.get("status"));

        // Period
        if (stripeData.get("current_period_start") != null) {
            subscription.setCurrentPeriodStart(epochToLocalDateTime(((Number) stripeData.get("current_period_start")).longValue()));
        }
        if (stripeData.get("current_period_end") != null) {
            subscription.setCurrentPeriodEnd(epochToLocalDateTime(((Number) stripeData.get("current_period_end")).longValue()));
        }

        // Cancellation
        subscription.setCancelAtPeriodEnd((Boolean) stripeData.get("cancel_at_period_end"));

        if (stripeData.get("cancel_at") != null) {
            subscription.setCancelAt(epochToLocalDateTime(((Number) stripeData.get("cancel_at")).longValue()));
        }
        if (stripeData.get("canceled_at") != null) {
            subscription.setCanceledAt(epochToLocalDateTime(((Number) stripeData.get("canceled_at")).longValue()));
        }

        // Trial
        if (stripeData.get("trial_start") != null) {
            subscription.setTrialStart(epochToLocalDateTime(((Number) stripeData.get("trial_start")).longValue()));
        }
        if (stripeData.get("trial_end") != null) {
            subscription.setTrialEnd(epochToLocalDateTime(((Number) stripeData.get("trial_end")).longValue()));
        }
    }

    private LocalDateTime epochToLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) return null;
        return Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC).toLocalDateTime();
    }
}
