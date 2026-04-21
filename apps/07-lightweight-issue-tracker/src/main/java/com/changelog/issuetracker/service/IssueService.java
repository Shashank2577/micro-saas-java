package com.changelog.issuetracker.service;

import com.changelog.ai.AiService;
import lombok.extern.slf4j.Slf4j;
import com.changelog.dto.AiDuplicateCheckResponse;
import com.changelog.dto.AiPriorityResponse;
import com.changelog.issuetracker.model.Comment;
import com.changelog.issuetracker.model.Issue;
import com.changelog.issuetracker.model.IssueEvent;
import com.changelog.issuetracker.repository.CommentRepository;
import com.changelog.issuetracker.repository.IssueEventRepository;
import com.changelog.issuetracker.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.changelog.exception.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueService {
    private final IssueRepository issueRepository;
    private final CommentRepository commentRepository;
    private final IssueEventRepository issueEventRepository;
    private final AiService aiService;

    public List<Issue> getIssuesByProject(UUID projectId, UUID tenantId) {
        return issueRepository.findAllByTenantIdAndProjectId(tenantId, projectId);
    }

    public Issue getIssue(UUID id, UUID tenantId) {
        return issueRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found"));
    }

    @Transactional
    public Issue createIssue(Issue issue) {
        if (issue.getId() == null) {
            issue.setId(UUID.randomUUID());
        }
        
        Long maxNumber = issueRepository.findMaxNumberByProjectId(issue.getProject().getId());
        issue.setNumber(maxNumber == null ? 1L : maxNumber + 1);
        
        Issue savedIssue = issueRepository.save(issue);
        logEvent(savedIssue, issue.getReporterId(), "ISSUE_CREATED", null, "Created");
        return savedIssue;
    }

    @Transactional
    public Issue updateIssue(UUID id, Issue issueDetails, UUID tenantId, UUID actorId) {
        Issue issue = getIssue(id, tenantId);
        
        if (!issue.getStatus().equals(issueDetails.getStatus())) {
            logEvent(issue, actorId, "STATUS_CHANGED", issue.getStatus(), issueDetails.getStatus());
            issue.setStatus(issueDetails.getStatus());
        }
        
        if (!issue.getPriority().equals(issueDetails.getPriority())) {
            logEvent(issue, actorId, "PRIORITY_CHANGED", issue.getPriority(), issueDetails.getPriority());
            issue.setPriority(issueDetails.getPriority());
        }

        issue.setTitle(issueDetails.getTitle());
        issue.setDescription(issueDetails.getDescription());
        issue.setAssigneeId(issueDetails.getAssigneeId());
        issue.setDueDate(issueDetails.getDueDate());
        
        return issueRepository.save(issue);
    }

    @Transactional
    public void deleteIssue(UUID id, UUID tenantId) {
        Issue issue = getIssue(id, tenantId);
        issueRepository.delete(issue);
    }

    public List<Issue> searchIssues(String query, UUID tenantId) {
        return issueRepository.searchIssues(tenantId, query);
    }

    public List<IssueEvent> getIssueActivity(UUID issueId) {
        return issueEventRepository.findAllByIssueIdOrderByCreatedAtDesc(issueId);
    }

    @Transactional
    public Comment addComment(UUID issueId, Comment comment, UUID tenantId) {
        Issue issue = getIssue(issueId, tenantId);
        comment.setId(UUID.randomUUID());
        comment.setIssue(issue);
        Comment savedComment = commentRepository.save(comment);
        logEvent(issue, comment.getAuthorId(), "COMMENT_ADDED", null, "Comment ID: " + savedComment.getId());
        return savedComment;
    }

    public List<Comment> getComments(UUID issueId) {
        return commentRepository.findAllByIssueId(issueId);
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID tenantId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        // Check tenant isolation via issue
        getIssue(comment.getIssue().getId(), tenantId);
        commentRepository.delete(comment);
    }

    @Transactional
    public Comment updateComment(UUID commentId, String content, UUID tenantId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        // Check tenant isolation via issue
        getIssue(comment.getIssue().getId(), tenantId);
        comment.setContent(content);
        return commentRepository.save(comment);
    }

    public AiDuplicateCheckResponse checkDuplicate(String title, String description, UUID tenantId) {
        List<Issue> recentIssues = issueRepository.findAllByTenantId(tenantId);
        List<String> existingIssueTitles = recentIssues.stream()
                .map(i -> i.getTitle())
                .collect(Collectors.toList());
        
        return aiService.checkDuplicateIssue(title, description, existingIssueTitles);
    }

    public AiPriorityResponse suggestPriority(UUID issueId, UUID tenantId) {
        Issue issue = getIssue(issueId, tenantId);
        return aiService.suggestIssuePriority(issue.getTitle(), issue.getDescription());
    }

    private void logEvent(Issue issue, UUID actorId, String eventType, String oldValue, String newValue) {
        IssueEvent event = IssueEvent.builder()
                .id(UUID.randomUUID())
                .issue(issue)
                .actorId(actorId)
                .eventType(eventType)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        issueEventRepository.save(event);
    }
}
