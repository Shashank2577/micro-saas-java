package com.changelog.controller;

import com.changelog.dto.DashboardStatsResponse;
import com.changelog.model.Post;
import com.changelog.repository.PostRepository;
import com.changelog.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("coreAnalyticsController")
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ProjectRepository projectRepository;
    private final PostRepository postRepository;

    @GetMapping("/stats/{tenantId}")
    @PreAuthorize("@tenantSecurity.isMember(authentication, #tenantId)")
    public DashboardStatsResponse getStats(@PathVariable UUID tenantId) {
        return DashboardStatsResponse.builder()
                .totalProjects(projectRepository.findByTenantId(tenantId).size())
                .totalPosts(postRepository.countByTenantId(tenantId))
                .publishedPosts(postRepository.countByTenantIdAndStatus(tenantId, Post.PostStatus.PUBLISHED))
                .build();
    }
}
