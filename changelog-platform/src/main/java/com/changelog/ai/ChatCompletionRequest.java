package com.changelog.ai;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChatCompletionRequest {
    private String model;
    private List<Map<String, String>> messages;

    public ChatCompletionRequest(String model, List<Map<String, String>> messages) {
        this.model = model;
        this.messages = messages;
    }
}
