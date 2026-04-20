package com.changelog.repository;

import com.changelog.model.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, UUID> {
    Optional<TaskSubmission> findByTaskInstanceId(UUID taskInstanceId);
}
