package com.changelog.invoice.repository;

import com.changelog.invoice.domain.InvoiceSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InvoiceSettingsRepository extends JpaRepository<InvoiceSettings, UUID> {
}
