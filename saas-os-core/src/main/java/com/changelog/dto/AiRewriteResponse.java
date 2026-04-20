package com.changelog.dto;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AiRewriteResponse {
    private final String rewritten;

    public String getRewritten() { return rewritten; }
}
