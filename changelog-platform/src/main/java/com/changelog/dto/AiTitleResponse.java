package com.changelog.dto;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class AiTitleResponse {
    private final List<String> titles;

    public List<String> getTitles() { return titles; }
}
