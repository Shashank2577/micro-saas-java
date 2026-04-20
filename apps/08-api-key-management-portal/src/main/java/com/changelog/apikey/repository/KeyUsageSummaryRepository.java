package com.changelog.apikey.repository;

import com.changelog.apikey.model.KeyUsageSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KeyUsageSummaryRepository extends JpaRepository<KeyUsageSummary, KeyUsageSummary.KeyUsageSummaryId> {
    List<KeyUsageSummary> findByKeyId(UUID keyId);
}
