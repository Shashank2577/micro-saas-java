package com.changelog.dto;

import com.changelog.model.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private UUID id;
    private UUID projectId;
    private String title;
    private String summary;
    private String content;
    private Post.PostStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime scheduledFor;
    private UUID authorId;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<TagResponse> tags;

    public static PostResponse fromEntity(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .projectId(post.getProjectId())
                .title(post.getTitle())
                .summary(post.getSummary())
                .content(post.getContent())
                .status(post.getStatus())
                .publishedAt(post.getPublishedAt())
                .scheduledFor(post.getScheduledFor())
                .authorId(post.getAuthorId())
                .viewCount(post.getViewCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .tags(post.getTags().stream()
                        .map(tag -> TagResponse.builder()
                                .id(tag.getId())
                                .name(tag.getName())
                                .color(tag.getColor())
                                .build())
                        .collect(java.util.stream.Collectors.toSet()))
                .build();
    }
}
