package com.changelog.business.monetization.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeCheckoutService {

    @Value("${stripe.secret-key:sk_test_placeholder}")
    private String stripeSecretKey;

    /**
     * Create a Stripe Checkout Session for a subscription
     */
    public Session createCheckoutSession(
            UUID tenantId,
            UUID customerId,
            String priceId,
            String successUrl,
            String cancelUrl) throws StripeException {
        
        Stripe.apiKey = stripeSecretKey;

        SessionCreateParams params = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .putMetadata("tenant_id", tenantId.toString())
                .putMetadata("customer_id", customerId.toString())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build()
                )
                .build();

        log.info("Requesting Stripe Checkout session for tenant={}, customer={}", tenantId, customerId);
        return Session.create(params);
    }

    /**
     * Retrieve a Checkout Session by ID
     */
    public Session getSession(String sessionId) throws StripeException {
        Stripe.apiKey = stripeSecretKey;
        return Session.retrieve(sessionId);
    }
}
