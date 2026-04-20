package com.changelog.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class FeedbackPostDto {
    private UUID id;
    private String title;
    private String description;
    private String status;
    private String submitterName;
    private int voteCount;
    private String etaLabel;
    private Instant createdAt;
}
