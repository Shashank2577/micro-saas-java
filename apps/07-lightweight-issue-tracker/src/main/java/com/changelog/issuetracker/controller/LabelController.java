package com.changelog.issuetracker.controller;

import com.changelog.config.TenantResolver;
import com.changelog.issuetracker.model.Label;
import com.changelog.issuetracker.service.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/labels")
@RequiredArgsConstructor
public class LabelController {
    private final LabelService labelService;
    private final TenantResolver tenantResolver;

    private UUID getTenantId(Jwt jwt) {
        return tenantResolver.getTenantId(jwt);
    }

    @GetMapping
    public List<Label> getAllLabels(@AuthenticationPrincipal Jwt jwt) {
        return labelService.getAllLabels(getTenantId(jwt));
    }

    @PostMapping
    public Label createLabel(@AuthenticationPrincipal Jwt jwt, @RequestBody Label label) {
        label.setTenantId(getTenantId(jwt));
        return labelService.createLabel(label);
    }
}
