package com.changelog.repository;

import com.changelog.model.PageVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PageVersionRepository extends JpaRepository<PageVersion, UUID> {
    List<PageVersion> findByPageIdOrderByVersionNumDesc(UUID pageId);
    Optional<PageVersion> findByPageIdAndVersionNum(UUID pageId, Integer versionNum);
}