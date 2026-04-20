package com.changelog.repository;

import com.changelog.model.PostVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostVoteRepository extends JpaRepository<PostVote, UUID> {
    Optional<PostVote> findByPostIdAndVoterEmail(UUID postId, String voterEmail);
    List<PostVote> findByPostId(UUID postId);
}
