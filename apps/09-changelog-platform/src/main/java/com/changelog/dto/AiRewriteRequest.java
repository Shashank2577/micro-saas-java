package com.changelog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AiRewriteRequest {
    @NotBlank(message = "Content is required")
    private String content;

    @Pattern(regexp = "formal|casual|technical", message = "Tone must be formal, casual, or technical")
    private String tone = "formal";
}
