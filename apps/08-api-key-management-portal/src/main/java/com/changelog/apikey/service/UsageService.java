package com.changelog.apikey.service;

import com.changelog.apikey.model.KeyUsageEvent;
import com.changelog.apikey.model.KeyUsageSummary;
import com.changelog.apikey.repository.KeyUsageEventRepository;
import com.changelog.apikey.repository.KeyUsageSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsageService {
    private final KeyUsageEventRepository usageEventRepository;
    private final KeyUsageSummaryRepository usageSummaryRepository;

    public void trackUsage(UUID tenantId, UUID keyId, String endpoint, String method, int statusCode, Integer responseMs, String ipAddress) {
        KeyUsageEvent event = KeyUsageEvent.builder()
                .tenantId(tenantId)
                .keyId(keyId)
                .endpoint(endpoint)
                .method(method)
                .statusCode(statusCode)
                .responseMs(responseMs)
                .ipAddress(ipAddress)
                .occurredAt(LocalDateTime.now())
                .build();
        usageEventRepository.save(event);
    }

    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void aggregateUsage() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);

        // This is a simplified aggregation. In a real system, you'd use a more efficient query.
        List<KeyUsageEvent> recentEvents = usageEventRepository.findAll(); // Should filter by date in real usage

        Map<UUID, List<KeyUsageEvent>> eventsByKey = recentEvents.stream()
                .collect(Collectors.groupingBy(KeyUsageEvent::getKeyId));

        for (Map.Entry<UUID, List<KeyUsageEvent>> entry : eventsByKey.entrySet()) {
            UUID keyId = entry.getKey();
            List<KeyUsageEvent> keyEvents = entry.getValue();

            long requestCount = keyEvents.size();
            long errorCount = keyEvents.stream().filter(e -> e.getStatusCode() >= 400).count();

            updateSummary(keyId, "day", LocalDate.now(), requestCount, errorCount);
        }
    }

    private void updateSummary(UUID keyId, String period, LocalDate start, long reqIncr, long errIncr) {
        KeyUsageSummary.KeyUsageSummaryId id = new KeyUsageSummary.KeyUsageSummaryId(keyId, period, start);
        KeyUsageSummary summary = usageSummaryRepository.findById(id)
                .orElse(KeyUsageSummary.builder()
                        .keyId(keyId)
                        .period(period)
                        .periodStart(start)
                        .requestCount(0)
                        .errorCount(0)
                        .build());

        summary.setRequestCount(summary.getRequestCount() + reqIncr);
        summary.setErrorCount(summary.getErrorCount() + errIncr);
        usageSummaryRepository.save(summary);
    }
}
