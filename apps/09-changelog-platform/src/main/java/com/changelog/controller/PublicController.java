package com.changelog.controller;

import com.changelog.dto.PostResponse;
import com.changelog.dto.ProjectResponse;
import com.changelog.service.PostService;
import com.changelog.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/p/{tenantSlug}/{projectSlug}")
@RequiredArgsConstructor
public class PublicController {

    private final ProjectService projectService;
    private final PostService postService;

    @GetMapping
    public ResponseEntity<ProjectData> getPublicProject(@PathVariable String tenantSlug,
                                                        @PathVariable String projectSlug) {
        ProjectResponse project = projectService.getProjectBySlug(tenantSlug, projectSlug);
        List<PostResponse> posts = postService.getPublishedPosts(project.getId());

        return ResponseEntity.ok(new ProjectData(project, posts));
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostResponse> getPublicPost(@PathVariable String tenantSlug,
                                                      @PathVariable String projectSlug,
                                                      @PathVariable String postId) {
        ProjectResponse project = projectService.getProjectBySlug(tenantSlug, projectSlug);
        PostResponse post = postService.getPost(project.getTenantId(), java.util.UUID.fromString(postId));

        if (!post.getStatus().equals(com.changelog.model.Post.PostStatus.PUBLISHED)) {
            return ResponseEntity.notFound().build();
        }

        postService.incrementViewCount(java.util.UUID.fromString(postId));

        return ResponseEntity.ok(post);
    }

    @PostMapping("/posts/{postId}/view")
    public ResponseEntity<Void> trackView(@PathVariable String postId) {
        postService.incrementViewCount(java.util.UUID.fromString(postId));
        return ResponseEntity.ok().build();
    }

    private record ProjectData(ProjectResponse project, List<PostResponse> posts) {}
}
