package com.changelog.issuetracker.repository;

import com.changelog.issuetracker.model.Label;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabelRepository extends JpaRepository<Label, UUID> {
    List<Label> findAllByTenantId(UUID tenantId);
}
