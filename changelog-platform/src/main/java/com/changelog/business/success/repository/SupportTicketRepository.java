package com.changelog.business.success.repository;

import com.changelog.business.success.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    List<SupportTicket> findByTenantId(UUID tenantId);

    List<SupportTicket> findByCustomerId(UUID customerId);

    @Query("SELECT t FROM SupportTicket t WHERE t.tenantId = :tenantId AND t.status IN :statuses ORDER BY t.priority DESC, t.createdAt ASC")
    List<SupportTicket> findByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);

    @Query("SELECT t FROM SupportTicket t WHERE t.tenantId = :tenantId AND t.assignedTo = :userId AND t.status IN ('open', 'in_progress')")
    List<SupportTicket> findAssignedTickets(UUID tenantId, UUID userId);

    @Query("SELECT t FROM SupportTicket t WHERE t.tenantId = :tenantId AND t.sentiment = :sentiment ORDER BY t.createdAt DESC")
    List<SupportTicket> findByTenantIdAndSentiment(UUID tenantId, String sentiment);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
