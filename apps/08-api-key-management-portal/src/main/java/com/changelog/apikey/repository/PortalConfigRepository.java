package com.changelog.apikey.repository;

import com.changelog.apikey.model.PortalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PortalConfigRepository extends JpaRepository<PortalConfig, UUID> {
}
