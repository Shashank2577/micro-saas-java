package com.changelog.okr.repository;

import com.changelog.okr.model.KrCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KrCheckInRepository extends JpaRepository<KrCheckIn, UUID> {
    List<KrCheckIn> findAllByKeyResultIdOrderByWeekStartDesc(UUID keyResultId);
    Optional<KrCheckIn> findByKeyResultIdAndWeekStart(UUID keyResultId, LocalDate weekStart);
}
