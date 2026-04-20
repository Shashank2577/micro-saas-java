package com.changelog.invoice.repository;

import com.changelog.invoice.domain.InvoiceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceEventRepository extends JpaRepository<InvoiceEvent, UUID> {
    List<InvoiceEvent> findAllByInvoiceIdOrderByOccurredAtDesc(UUID invoiceId);
}
