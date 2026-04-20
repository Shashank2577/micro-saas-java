package com.changelog.invoice.repository;

import com.changelog.invoice.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findAllByTenantId(UUID tenantId);
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Invoice> findByPublicToken(String publicToken);
    List<Invoice> findByStatusAndDueDateBefore(String status, java.time.LocalDate date);
}
