package com.changelog.business.intelligence.repository;

import com.changelog.business.intelligence.model.UnitEconomics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitEconomicsRepository extends JpaRepository<UnitEconomics, UUID> {

    @Query("SELECT u FROM UnitEconomics u WHERE u.tenantId = :tenantId ORDER BY u.period DESC")
    List<UnitEconomics> findByTenantIdOrderByPeriodDesc(UUID tenantId);

    @Query("SELECT u FROM UnitEconomics u WHERE u.tenantId = :tenantId ORDER BY u.period DESC LIMIT 1")
    Optional<UnitEconomics> findFirstByTenantIdOrderByPeriodDesc(UUID tenantId);

    @Query("SELECT u FROM UnitEconomics u WHERE u.tenantId = :tenantId AND u.period >= :since ORDER BY u.period DESC")
    List<UnitEconomics> findByTenantIdOrderByPeriodDesc(UUID tenantId, LocalDate since);
}
