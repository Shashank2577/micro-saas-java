package com.changelog.invoice.repository;

import com.changelog.invoice.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    List<Client> findAllByTenantId(UUID tenantId);
    Optional<Client> findByIdAndTenantId(UUID id, UUID tenantId);
}
