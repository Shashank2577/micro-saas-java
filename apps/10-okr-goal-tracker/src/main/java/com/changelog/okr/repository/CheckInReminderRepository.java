package com.changelog.okr.repository;

import com.changelog.okr.model.CheckInReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CheckInReminderRepository extends JpaRepository<CheckInReminder, UUID> {
    Optional<CheckInReminder> findByUserIdAndWeekStart(UUID userId, LocalDate weekStart);
}
