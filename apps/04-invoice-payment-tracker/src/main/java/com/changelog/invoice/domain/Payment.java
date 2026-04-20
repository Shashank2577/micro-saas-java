package com.changelog.invoice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String method;

    private String reference;

    @Column(name = "paid_at", nullable = false)
    private ZonedDateTime paidAt;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @PrePersist
    protected void onCreate() {
        if (method == null) method = "online";
        if (paidAt == null) paidAt = ZonedDateTime.now();
    }
}
