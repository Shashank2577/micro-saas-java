package com.changelog.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsResponse {
    private long totalProjects;
    private long totalPosts;
    private long publishedPosts;
}
