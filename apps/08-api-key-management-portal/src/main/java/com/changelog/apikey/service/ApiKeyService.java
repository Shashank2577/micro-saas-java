package com.changelog.apikey.service;

import com.changelog.apikey.model.ApiKey;
import com.changelog.apikey.model.KeyAuditEvent;
import com.changelog.apikey.repository.ApiKeyRepository;
import com.changelog.apikey.repository.KeyAuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private final ApiKeyRepository apiKeyRepository;
    private final KeyAuditEventRepository auditEventRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final String KEY_PREFIX = "sk_live_";

    @Transactional
    public String createKey(UUID tenantId, UUID consumerId, String name, List<String> scopes, String environment, String createdBy) {
        String rawKey = generateRawKey();
        String keyHash = passwordEncoder.encode(rawKey);
        String prefix = KEY_PREFIX + rawKey.substring(0, 8);

        ApiKey apiKey = ApiKey.builder()
                .tenantId(tenantId)
                .consumerId(consumerId)
                .name(name)
                .keyPrefix(prefix)
                .keyHash(keyHash)
                .scopes(scopes)
                .environment(environment != null ? environment : "production")
                .status("active")
                .createdBy(createdBy)
                .build();

        apiKey = apiKeyRepository.save(apiKey);

        logAuditEvent(tenantId, consumerId, apiKey.getId(), "key_created", createdBy, Map.of("name", name));

        return rawKey;
    }

    @Transactional
    public void revokeKey(UUID tenantId, UUID keyId, String revokedBy) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("Key not found"));

        if (!apiKey.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized");
        }

        apiKey.setStatus("revoked");
        apiKey.setRevokedAt(LocalDateTime.now());
        apiKey.setRevokedBy(revokedBy);
        apiKeyRepository.save(apiKey);

        logAuditEvent(tenantId, apiKey.getConsumerId(), keyId, "key_revoked", revokedBy, null);
    }

    @Transactional
    public String rotateKey(UUID tenantId, UUID keyId, String rotatedBy) {
        ApiKey oldKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("Key not found"));

        if (!oldKey.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized");
        }

        String newRawKey = createKey(tenantId, oldKey.getConsumerId(), oldKey.getName() + " (Rotated)", 
                oldKey.getScopes(), oldKey.getEnvironment(), rotatedBy);

        // Immediate revocation of old key as per basic requirement, 
        // though overlap window is better in real-world
        oldKey.setStatus("revoked");
        oldKey.setRevokedAt(LocalDateTime.now());
        oldKey.setRevokedBy(rotatedBy);
        apiKeyRepository.save(oldKey);

        logAuditEvent(tenantId, oldKey.getConsumerId(), keyId, "key_rotated", rotatedBy, Map.of("new_key_id", keyId));

        return newRawKey;
    }

    public Optional<ApiKey> validateKey(String rawKey) {
        if (!rawKey.startsWith(KEY_PREFIX) || rawKey.length() < 32) {
            return Optional.empty();
        }

        String prefix = rawKey.substring(0, KEY_PREFIX.length() + 8);
        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefix(prefix);

        for (ApiKey key : candidates) {
            if ("active".equals(key.getStatus()) && passwordEncoder.matches(rawKey, key.getKeyHash())) {
                key.setLastUsedAt(LocalDateTime.now());
                apiKeyRepository.save(key);
                return Optional.of(key);
            }
        }

        return Optional.empty();
    }

    private String generateRawKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void logAuditEvent(UUID tenantId, UUID consumerId, UUID keyId, String eventType, String actor, Map<String, Object> metadata) {
        KeyAuditEvent event = KeyAuditEvent.builder()
                .tenantId(tenantId)
                .consumerId(consumerId)
                .keyId(keyId)
                .eventType(eventType)
                .actor(actor)
                .metadata(metadata)
                .occurredAt(LocalDateTime.now())
                .build();
        auditEventRepository.save(event);
    }
}
