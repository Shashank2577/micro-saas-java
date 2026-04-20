package com.changelog.business.monetization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionRequest {

    @NotNull
    private UUID tenantId;

    @NotNull
    private UUID customerId;

    @NotBlank
    private String priceId;

    @NotBlank
    private String successUrl;

    @NotBlank
    private String cancelUrl;
}
