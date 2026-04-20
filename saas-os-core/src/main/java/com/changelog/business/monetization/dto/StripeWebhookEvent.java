package com.changelog.business.monetization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeWebhookEvent {
    private String id;
    private String type;
    private Instant created;

    @JsonProperty("data")
    private DataObject data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataObject {
        private ObjectWrapper object;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObjectWrapper {
        private String id;
        private String object; // "subscription", "invoice", etc.
        private Map<String, Object> attributes;
    }
}
