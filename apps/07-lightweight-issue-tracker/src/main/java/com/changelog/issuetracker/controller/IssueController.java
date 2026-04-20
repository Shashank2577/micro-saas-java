package com.changelog.issuetracker.controller;

import com.changelog.config.TenantResolver;
import com.changelog.dto.AiDuplicateCheckResponse;
import com.changelog.dto.AiPriorityResponse;
import com.changelog.issuetracker.model.Comment;
import com.changelog.issuetracker.model.Issue;
import com.changelog.issuetracker.model.IssueEvent;
import com.changelog.issuetracker.model.Project;
import com.changelog.issuetracker.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IssueController {
    private final IssueService issueService;
    private final TenantResolver tenantResolver;

    private UUID getTenantId(Jwt jwt) {
        return tenantResolver.getTenantId(jwt);
    }

    private UUID getUserId(Jwt jwt) {
        return tenantResolver.getUserId(jwt);
    }

    @GetMapping("/projects/{projectId}/issues")
    public List<Issue> getIssuesByProject(@AuthenticationPrincipal Jwt jwt, @PathVariable("projectId") UUID projectId) {
        return issueService.getIssuesByProject(projectId, getTenantId(jwt));
    }

    @PostMapping("/projects/{projectId}/issues")
    public Issue createIssue(@AuthenticationPrincipal Jwt jwt, @PathVariable("projectId") UUID projectId, @RequestBody Issue issue) {
        issue.setTenantId(getTenantId(jwt));
        issue.setProject(Project.builder().id(projectId).build());
        issue.setReporterId(getUserId(jwt));
        if (issue.getStatus() == null) issue.setStatus("OPEN");
        if (issue.getPriority() == null) issue.setPriority("MEDIUM");
        return issueService.createIssue(issue);
    }

    @GetMapping("/issues/{issueId}")
    public Issue getIssue(@AuthenticationPrincipal Jwt jwt, @PathVariable("issueId") UUID issueId) {
        return issueService.getIssue(issueId, getTenantId(jwt));
    }

    @PutMapping("/issues/{issueId}")
    public Issue updateIssue(@AuthenticationPrincipal Jwt jwt, @PathVariable("issueId") UUID issueId, @RequestBody Issue issue) {
        return issueService.updateIssue(issueId, issue, getTenantId(jwt), getUserId(jwt));
    }

    @DeleteMapping("/issues/{issueId}")
    public void deleteIssue(@AuthenticationPrincipal Jwt jwt, @PathVariable("issueId") UUID issueId) {
        issueService.deleteIssue(issueId, getTenantId(jwt));
    }

    @GetMapping("/issues/{issueId}/activity")
    public List<IssueEvent> getIssueActivity(@AuthenticationPrincipal Jwt jwt, @PathVariable("issueId") UUID issueId) {
        return issueService.getIssueActivity(issueId);
    }

    @GetMapping("/issues/{issueId}/comments")
    public List<Comment> getComments(@AuthenticationPrincipal Jwt jwt, @PathVariable("issueId") UUID issueId) {
        return issueService.getComments(issueId);
    }

    @PostMapping("/issues/{issueId}/comments")
    public Comment addComment(@AuthenticationPrincipal Jwt jwt, @PathVariable("issueId") UUID issueId, @RequestBody Comment comment) {
        comment.setAuthorId(getUserId(jwt));
        return issueService.addComment(issueId, comment, getTenantId(jwt));
    }

    @PutMapping("/comments/{commentId}")
    public Comment updateComment(@AuthenticationPrincipal Jwt jwt, @PathVariable("commentId") UUID commentId, @RequestBody Comment commentDetails) {
        return issueService.updateComment(commentId, commentDetails.getContent(), getTenantId(jwt));
    }

    @DeleteMapping("/comments/{commentId}")
    public void deleteComment(@AuthenticationPrincipal Jwt jwt, @PathVariable("commentId") UUID commentId) {
        issueService.deleteComment(commentId, getTenantId(jwt));
    }

    @GetMapping("/issues/search")
    public List<Issue> searchIssues(@AuthenticationPrincipal Jwt jwt, @RequestParam("q") String q) {
        return issueService.searchIssues(q, getTenantId(jwt));
    }

    @PostMapping("/issues/ai/check-duplicate")
    public AiDuplicateCheckResponse checkDuplicate(@AuthenticationPrincipal Jwt jwt, @RequestBody Issue issue) {
        return issueService.checkDuplicate(issue.getTitle(), issue.getDescription(), getTenantId(jwt));
    }

    @PostMapping("/issues/{issueId}/ai/suggest-priority")
    public AiPriorityResponse suggestPriority(@AuthenticationPrincipal Jwt jwt, @PathVariable("issueId") UUID issueId) {
        return issueService.suggestPriority(issueId, getTenantId(jwt));
    }
}
