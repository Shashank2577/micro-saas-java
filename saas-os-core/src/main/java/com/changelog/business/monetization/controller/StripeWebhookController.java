package com.changelog.business.monetization.controller;

import com.changelog.business.monetization.dto.StripeWebhookEvent;
import com.changelog.business.monetization.service.StripeWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.SignatureException;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeWebhookService webhookService;
    private final ObjectMapper objectMapper;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    /**
     * Handle Stripe webhook events
     * POST /api/v1/webhooks/stripe
     */
    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            HttpServletRequest request) {

        try {
            // Verify Stripe signature
            verifyStripeSignature(payload, signature);

            // Parse webhook event
            StripeWebhookEvent event = objectMapper.readValue(payload, StripeWebhookEvent.class);

            // Process event
            webhookService.handleWebhook(event, event.getId());

            return ResponseEntity.ok().build();

        } catch (SignatureVerificationException e) {
            log.error("Stripe signature verification failed", e);
            return ResponseEntity.status(401).build();
        } catch (IOException e) {
            log.error("Error parsing webhook payload", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Test endpoint for webhook verification
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testWebhook() {
        return ResponseEntity.ok(Map.of(
                "status", "Webhook endpoint is ready",
                "instructions", "Send POST request with Stripe-Signature header"
        ));
    }

    /**
     * Verify Stripe webhook signature
     */
    private void verifyStripeSignature(String payload, String signature) throws SignatureVerificationException {
        if (StringUtils.hasText(webhookSecret)) {
            Webhook.constructEvent(payload, signature, webhookSecret);
        } else {
            log.warn("Stripe webhook secret is not configured. Skipping signature verification (LOCAL ONLY).");
        }
    }
}
