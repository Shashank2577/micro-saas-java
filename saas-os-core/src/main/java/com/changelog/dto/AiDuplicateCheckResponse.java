package com.changelog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDuplicateCheckResponse {
    private boolean isDuplicate;
    private Double confidenceScore;
    private String reason;
}
