package com.changelog.repository;

import com.changelog.model.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoardRepository extends JpaRepository<Board, UUID> {
    List<Board> findByTenantId(UUID tenantId);
    Optional<Board> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Board> findByTenantIdAndSlug(UUID tenantId, String slug);
}
