package com.changelog.repository;

import com.changelog.model.TemplateTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TemplateTaskRepository extends JpaRepository<TemplateTask, UUID> {
    List<TemplateTask> findByTemplateIdOrderByPositionAsc(UUID templateId);
    void deleteByTemplateId(UUID templateId);
}
