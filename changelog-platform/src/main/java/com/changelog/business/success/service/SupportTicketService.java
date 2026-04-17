package com.changelog.business.success.service;

import com.changelog.business.success.dto.CreateSupportTicketRequest;
import com.changelog.business.success.model.SupportTicket;
import com.changelog.business.success.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;

    public List<SupportTicket> getAll(UUID tenantId) {
        return supportTicketRepository.findByTenantId(tenantId);
    }

    public Optional<SupportTicket> getById(UUID tenantId, UUID id) {
        return supportTicketRepository.findById(id)
                .filter(ticket -> ticket.getTenantId().equals(tenantId));
    }

    @Transactional
    public SupportTicket create(UUID tenantId, CreateSupportTicketRequest request) {
        SupportTicket ticket = SupportTicket.builder()
                .tenantId(tenantId)
                .customerId(request.getCustomerId())
                .subject(request.getSubject())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : "normal")
                .channel(request.getChannel())
                .build();

        return supportTicketRepository.save(ticket);
    }

    @Transactional
    public Optional<SupportTicket> updateStatus(UUID tenantId, UUID id, String status) {
        return supportTicketRepository.findById(id)
                .filter(ticket -> ticket.getTenantId().equals(tenantId))
                .map(ticket -> {
                    ticket.setStatus(status);
                    return supportTicketRepository.save(ticket);
                });
    }

    @Transactional
    public Optional<SupportTicket> close(UUID tenantId, UUID id) {
        return supportTicketRepository.findById(id)
                .filter(ticket -> ticket.getTenantId().equals(tenantId))
                .map(ticket -> {
                    ticket.setStatus("closed");
                    ticket.setResolvedAt(LocalDateTime.now());
                    return supportTicketRepository.save(ticket);
                });
    }
}
