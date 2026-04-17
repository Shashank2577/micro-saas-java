package com.changelog.controller;

import com.changelog.ai.AiService;
import com.changelog.dto.AiRewriteRequest;
import com.changelog.dto.AiRewriteResponse;
import com.changelog.dto.AiTitleRequest;
import com.changelog.dto.AiTitleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/rewrite")
    public ResponseEntity<AiRewriteResponse> rewrite(@Valid @RequestBody AiRewriteRequest request) {
        AiRewriteResponse response = aiService.rewrite(request.getContent(), request.getTone());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-title")
    public ResponseEntity<AiTitleResponse> generateTitle(@Valid @RequestBody AiTitleRequest request) {
        AiTitleResponse response = aiService.generateTitles(request.getContent());
        return ResponseEntity.ok(response);
    }
}
