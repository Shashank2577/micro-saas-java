package com.changelog.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PublicChangelogResponse {
    private String projectName;
    private String slug;
    private String description;
    private Map<String, Object> branding;
    private List<PostResponse> posts;
    private int totalPosts;
}
