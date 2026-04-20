package com.changelog.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class BoardDto {
    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String visibility;
    private Instant createdAt;
}
