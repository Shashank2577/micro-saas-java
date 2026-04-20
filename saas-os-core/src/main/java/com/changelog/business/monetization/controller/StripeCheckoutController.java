package com.changelog.business.monetization.controller;

import com.changelog.business.monetization.dto.CheckoutSessionRequest;
import com.changelog.business.monetization.dto.CheckoutSessionResponse;
import com.changelog.business.monetization.service.StripeCheckoutService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monetization/checkout")
@RequiredArgsConstructor
@Slf4j
public class StripeCheckoutController {

    private final StripeCheckoutService checkoutService;

    /**
     * Create a Stripe Checkout Session for a subscription
     */
    @PostMapping("/create-session")
    public ResponseEntity<CheckoutSessionResponse> createSession(
            @Valid @RequestBody CheckoutSessionRequest request) throws StripeException {
        
        log.info("Creating checkout session for tenant={}, priceId={}", 
                request.getTenantId(), request.getPriceId());

        Session session = checkoutService.createCheckoutSession(
                request.getTenantId(),
                request.getCustomerId(),
                request.getPriceId(),
                request.getSuccessUrl(),
                request.getCancelUrl()
        );

        return ResponseEntity.ok(CheckoutSessionResponse.builder()
                .sessionId(session.getId())
                .checkoutUrl(session.getUrl())
                .build());
    }

    /**
     * Get session status after redirect back from Stripe
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Session> getSession(@PathVariable String sessionId) throws StripeException {
        return ResponseEntity.ok(checkoutService.getSession(sessionId));
    }
}
