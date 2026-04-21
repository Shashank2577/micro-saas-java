package com.changelog.invoice.service;

import com.changelog.invoice.domain.Invoice;
import com.changelog.invoice.domain.Payment;
import com.changelog.invoice.repository.InvoiceRepository;
import com.changelog.invoice.repository.PaymentRepository;
import com.changelog.model.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.changelog.exception.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Invoice> listInvoices(UUID tenantId) {
        return invoiceRepository.findAllByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(UUID invoiceId, UUID tenantId) {
        return invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));
    }

    @Transactional(readOnly = true)
    public Invoice getInvoiceByPublicToken(String token) {
        return invoiceRepository.findByPublicToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));
    }

    @Transactional
    public Invoice createInvoice(UUID tenantId, Invoice invoice) {
        invoice.setTenant(entityManager.getReference(Tenant.class, tenantId));
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice updateStatus(UUID invoiceId, UUID tenantId, String status) {
        Invoice invoice = getInvoice(invoiceId, tenantId);
        invoice.setStatus(status);
        if ("sent".equals(status) && invoice.getSentAt() == null) {
            invoice.setSentAt(ZonedDateTime.now());
        }
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice recordPayment(UUID invoiceId, UUID tenantId, Payment payment) {
        Invoice invoice = getInvoice(invoiceId, tenantId);
        payment.setInvoice(invoice);
        paymentRepository.save(payment);
        invoice.setAmountPaid(invoice.getAmountPaid().add(payment.getAmount()));
        if (invoice.getAmountPaid().compareTo(invoice.getTotal()) >= 0) {
            invoice.setStatus("paid");
            invoice.setPaidAt(ZonedDateTime.now());
        }
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public void deleteInvoice(UUID invoiceId, UUID tenantId) {
        Invoice invoice = getInvoice(invoiceId, tenantId);
        invoiceRepository.delete(invoice);
    }
}
