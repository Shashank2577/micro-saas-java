package com.changelog.business.monetization;

import com.changelog.business.monetization.dto.CheckoutSessionRequest;
import com.changelog.business.monetization.service.StripeCheckoutService;
import com.stripe.model.checkout.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StripeCheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StripeCheckoutService checkoutService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreateCheckoutSession() throws Exception {
        // Mock Stripe Session
        Session mockSession = Mockito.mock(Session.class);
        Mockito.when(mockSession.getId()).thenReturn("cs_test_123");
        Mockito.when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_123");

        Mockito.when(checkoutService.createCheckoutSession(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(mockSession);

        CheckoutSessionRequest request = CheckoutSessionRequest.builder()
                .tenantId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .priceId("price_123")
                .successUrl("http://localhost:3000/success")
                .cancelUrl("http://localhost:3000/cancel")
                .build();

        mockMvc.perform(post("/api/v1/monetization/checkout/create-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("cs_test_123"))
            .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/pay/cs_test_123"));
    }
}
