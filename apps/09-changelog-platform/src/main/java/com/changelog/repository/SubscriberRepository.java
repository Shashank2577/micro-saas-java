package com.changelog.repository;

import com.changelog.model.Subscriber;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, UUID> {

    List<Subscriber> findByProjectId(UUID projectId);

    Page<Subscriber> findByProjectIdAndStatus(UUID projectId, Subscriber.SubscriberStatus status, Pageable pageable);

    Optional<Subscriber> findByProjectIdAndEmail(UUID projectId, String email);

    List<Subscriber> findByProjectIdAndPlanTier(UUID projectId, String planTier);

    boolean existsByProjectIdAndEmail(UUID projectId, String email);
}
