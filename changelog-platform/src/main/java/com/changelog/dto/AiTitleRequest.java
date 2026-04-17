package com.changelog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiTitleRequest {
    @NotBlank(message = "Content is required")
    private String content;
}
