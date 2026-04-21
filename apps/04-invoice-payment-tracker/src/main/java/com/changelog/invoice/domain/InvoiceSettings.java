package com.changelog.invoice.domain;

import com.changelog.model.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "invoice_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSettings {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "next_number", nullable = false)
    private Integer nextNumber;

    @Column(name = "number_prefix", nullable = false)
    private String numberPrefix;

    @Column(name = "logo_key")
    private String logoKey;

    @Column(name = "default_notes")
    private String defaultNotes;

    @Column(name = "bank_details")
    private String bankDetails;

    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency;

    @PrePersist
    protected void onCreate() {
        if (nextNumber == null) nextNumber = 1;
        if (numberPrefix == null) numberPrefix = "INV-";
        if (defaultCurrency == null) defaultCurrency = "USD";
    }
}
