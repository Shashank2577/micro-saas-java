package com.changelog.repository;

import com.changelog.model.PostStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostStatusHistoryRepository extends JpaRepository<PostStatusHistory, UUID> {
    List<PostStatusHistory> findByPostIdOrderByChangedAtDesc(UUID postId);
}
