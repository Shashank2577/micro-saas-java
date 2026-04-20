package com.changelog.apikey.controller;

import com.changelog.apikey.model.ApiKey;
import com.changelog.apikey.service.ApiKeyService;
import com.changelog.apikey.service.UsageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class ValidationController {
    private final ApiKeyService apiKeyService;
    private final UsageService usageService;

    @PostMapping("/validate")
    public ResponseEntity<?> validateKey(@RequestHeader("X-API-KEY") String rawKey, HttpServletRequest request) {
        Optional<ApiKey> keyOpt = apiKeyService.validateKey(rawKey);
        
        if (keyOpt.isPresent()) {
            ApiKey key = keyOpt.get();
            usageService.trackUsage(key.getTenantId(), key.getId(), request.getRequestURI(), 
                    request.getMethod(), HttpStatus.OK.value(), 0, request.getRemoteAddr());
            
            return ResponseEntity.ok(Map.of(
                "consumer_id", key.getConsumerId(),
                "scopes", key.getScopes(),
                "environment", key.getEnvironment(),
                "tenant_id", key.getTenantId()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
