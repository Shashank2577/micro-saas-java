package com.changelog.repository;

import com.changelog.model.TaskInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskInstanceRepository extends JpaRepository<TaskInstance, UUID> {
    List<TaskInstance> findByOnboardingInstanceIdOrderByDueDateAsc(UUID onboardingInstanceId);
    Optional<TaskInstance> findByIdAndOnboardingInstanceId(UUID id, UUID onboardingInstanceId);
    List<TaskInstance> findByDueDateAndStatus(LocalDate dueDate, String status);
    List<TaskInstance> findByDueDateBeforeAndStatus(LocalDate dueDate, String status);
}
