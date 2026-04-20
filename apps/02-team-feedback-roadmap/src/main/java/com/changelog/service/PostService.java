package com.changelog.service;

import com.changelog.model.Board;
import com.changelog.model.FeedbackPost;
import com.changelog.model.PostStatusHistory;
import com.changelog.model.PostVote;
import com.changelog.repository.FeedbackPostRepository;
import com.changelog.repository.PostStatusHistoryRepository;
import com.changelog.repository.PostVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostService {

    private final FeedbackPostRepository postRepository;
    private final PostVoteRepository voteRepository;
    private final PostStatusHistoryRepository historyRepository;
    private final BoardService boardService;

    @Transactional(readOnly = true)
    public List<FeedbackPost> getPostsByBoard(UUID boardId, UUID tenantId) {
        return postRepository.findByBoardIdAndTenantIdOrderByVoteCountDesc(boardId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<FeedbackPost> getPublicPostsByBoard(UUID boardId, UUID tenantId) {
        return postRepository.findByBoardIdAndTenantIdAndIsPublicTrueOrderByVoteCountDesc(boardId, tenantId);
    }

    @Transactional(readOnly = true)
    public FeedbackPost getPost(UUID postId, UUID tenantId) {
        return postRepository.findByIdAndTenantId(postId, tenantId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    @Transactional(readOnly = true)
    public FeedbackPost getPublicPost(UUID postId, UUID tenantId) {
        return postRepository.findByIdAndTenantIdAndIsPublicTrue(postId, tenantId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    @Transactional
    public FeedbackPost createPost(UUID boardId, UUID tenantId, FeedbackPost post) {
        Board board = boardService.getBoard(boardId, tenantId);
        post.setBoard(board);
        post.setTenantId(tenantId);
        return postRepository.save(post);
    }

    @Transactional
    public FeedbackPost updatePostStatus(UUID postId, UUID tenantId, String newStatus, UUID changedBy, String note) {
        FeedbackPost post = getPost(postId, tenantId);
        String oldStatus = post.getStatus();

        if (!oldStatus.equals(newStatus)) {
            post.setStatus(newStatus);
            post = postRepository.save(post);

            PostStatusHistory history = new PostStatusHistory();
            history.setPost(post);
            history.setOldStatus(oldStatus);
            history.setNewStatus(newStatus);
            history.setChangedBy(changedBy);
            history.setNote(note);
            historyRepository.save(history);

            if ("completed".equals(newStatus)) {
                notifyVotersOfCompletion(post);
            }
        }
        return post;
    }

    private void notifyVotersOfCompletion(FeedbackPost post) {
        List<PostVote> votes = voteRepository.findByPostId(post.getId());
        for (PostVote vote : votes) {
            // Simulating email sending via service log
            System.out.println("Email Sent to: " + vote.getVoterEmail() + " - Subject: Feature Completed! - Body: The feature you voted for (" + post.getTitle() + ") is now live.");
        }
    }

    @Transactional
    public void voteOnPost(UUID postId, UUID tenantId, String voterEmail, String voterName) {
        FeedbackPost post = getPost(postId, tenantId); // also validates tenant bounds

        Optional<PostVote> existingVote = voteRepository.findByPostIdAndVoterEmail(postId, voterEmail);
        if (existingVote.isEmpty()) {
            PostVote vote = new PostVote();
            vote.setPost(post);
            vote.setVoterEmail(voterEmail);
            vote.setVoterName(voterName);
            voteRepository.save(vote);

            post.setVoteCount(post.getVoteCount() + 1);
            postRepository.save(post);
        }
    }
}
