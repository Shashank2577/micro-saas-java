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
public class CreatePostRequest {
    private String title;
    private String summary;
    private String content;
    private Post.PostStatus status;
    private LocalDateTime scheduledFor;
    private Set<UUID> tagIds;
}
