package com.changelog.dto;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AiDuplicateCheckResponse {
    private final boolean duplicate;
    private final String similarIssueTitle;
    private final double confidence;

    public boolean isDuplicate() { return duplicate; }
    public String getSimilarIssueTitle() { return similarIssueTitle; }
    public double getConfidence() { return confidence; }
}
