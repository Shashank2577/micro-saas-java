package com.changelog.repository;

import com.changelog.model.AiQaSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiQaSessionRepository extends JpaRepository<AiQaSession, UUID> {
}