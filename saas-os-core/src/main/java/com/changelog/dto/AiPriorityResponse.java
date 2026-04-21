package com.changelog.dto;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AiPriorityResponse {
    private final String priority;
    private final String reasoning;

    public String getPriority() { return priority; }
    public String getReasoning() { return reasoning; }
}
