package com.changelog.ai;

import com.changelog.dto.AiDuplicateCheckResponse;
import com.changelog.dto.AiPriorityResponse;
import com.changelog.dto.AiRewriteResponse;
import com.changelog.dto.AiTitleResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final LiteLlmApi liteLlmApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.model:gpt-4}")
    private String model;

    public AiRewriteResponse rewrite(String content, String tone) {
        String prompt = String.format(
                "You are a professional technical writer. Rewrite the following release notes as polished, professional release notes.\n" +
                "Tone: %s\n" +
                "Content:\n%s",
                tone, content
        );

        String rewritten = callLlm(prompt);
        return new AiRewriteResponse(rewritten);
    }

    public AiTitleResponse generateTitles(String content) {
        String prompt = String.format(
                "Generate 3 catchy, clickable title options for the following changelog entry. " +
                "Return ONLY a JSON array of strings in this exact format: [\"title1\", \"title2\", \"title3\"]. " +
                "No markdown, no explanation, just the JSON array.\n\nContent:\n%s",
                content
        );

        String response = callLlm(prompt);
        List<String> titles = parseTitlesFromResponse(response);
        return new AiTitleResponse(titles);
    }

    public AiDuplicateCheckResponse checkDuplicateIssue(String title, String description, List<String> existingTitles) {
        String existing = String.join("\n", existingTitles);
        String prompt = String.format(
                "Check if the following issue is a duplicate of any in the list. " +
                "Return JSON: {\"duplicate\": true/false, \"similarIssueTitle\": \"...\", \"confidence\": 0.0-1.0}\n\n" +
                "New issue: %s - %s\n\nExisting issues:\n%s",
                title, description, existing
        );
        try {
            String response = callLlm(prompt);
            var node = objectMapper.readTree(response);
            return new AiDuplicateCheckResponse(
                    node.path("duplicate").asBoolean(false),
                    node.path("similarIssueTitle").asText(""),
                    node.path("confidence").asDouble(0.0)
            );
        } catch (Exception e) {
            return new AiDuplicateCheckResponse(false, "", 0.0);
        }
    }

    public AiPriorityResponse suggestIssuePriority(String title, String description) {
        String prompt = String.format(
                "Suggest priority for this issue. Return JSON: {\"priority\": \"low|medium|high|critical\", \"reasoning\": \"...\"}\n\n" +
                "Issue: %s - %s",
                title, description
        );
        try {
            String response = callLlm(prompt);
            var node = objectMapper.readTree(response);
            return new AiPriorityResponse(
                    node.path("priority").asText("medium"),
                    node.path("reasoning").asText("")
            );
        } catch (Exception e) {
            return new AiPriorityResponse("medium", "Could not determine priority");
        }
    }

    public String callLlmRaw(String prompt) {
        return callLlm(prompt);
    }

    private String callLlm(String prompt) {
        try {
            var messages = List.of(Map.of("role", "user", "content", prompt));
            var request = new ChatCompletionRequest(model, messages);
            var response = liteLlmApi.chatCompletions(request).execute();

            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("LLM call failed: " + response.code() + " " + response.message());
            }

            String content = response.body().getChoices().get(0).getMessage().getContent();
            if (content == null || content.isBlank()) {
                throw new RuntimeException("LLM returned empty content");
            }

            return content.trim();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }

    private List<String> parseTitlesFromResponse(String response) {
        String cleaned = response.trim();
        // Remove markdown code blocks if present
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        try {
            String[] titles = objectMapper.readValue(cleaned, String[].class);
            return Arrays.stream(titles).map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse titles from LLM response: " + cleaned, e);
        }
    }
}
