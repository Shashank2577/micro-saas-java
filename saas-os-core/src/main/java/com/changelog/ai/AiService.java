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

    public AiDuplicateCheckResponse checkDuplicateIssue(String newIssueTitle, String newIssueDescription, List<String> existingIssues) {
        String existingIssuesStr = existingIssues.stream().collect(Collectors.joining("\n- "));
        String prompt = String.format(
                "You are an AI assistant for a software development team. Check if the following new issue is a duplicate of any existing issues.\n\n" +
                "New Issue Title: %s\n" +
                "New Issue Description: %s\n\n" +
                "Existing Issues:\n- %s\n\n" +
                "Return ONLY a JSON object in this exact format: {\"duplicate\": true/false, \"confidenceScore\": 0.0-1.0, \"reason\": \"explanation\"}. " +
                "No markdown, no explanation, just the JSON object.",
                newIssueTitle, newIssueDescription, existingIssuesStr
        );

        try {
            String response = callLlm(prompt);
            return parseResponse(response, AiDuplicateCheckResponse.class);
        } catch (Exception e) {
            log.error("Fallback error during issue check: ", e);
            return new AiDuplicateCheckResponse(false, 0.0, "Fallback due to error");
        }
    }

    public AiPriorityResponse suggestIssuePriority(String title, String description) {
        String prompt = String.format(
                "You are an AI assistant for a software development team. Suggest a priority (LOW, MEDIUM, HIGH, URGENT) for the following issue based on its title and description.\n\n" +
                "Title: %s\n" +
                "Description: %s\n\n" +
                "Return ONLY a JSON object in this exact format: {\"priority\": \"PRIORITY\", \"reason\": \"explanation\"}. " +
                "No markdown, no explanation, just the JSON object.",
                title, description
        );

        try {
            String response = callLlm(prompt);
            return parseResponse(response, AiPriorityResponse.class);
        } catch (Exception e) {
            return new AiPriorityResponse("medium", "Fallback due to error");
        }
    }

    /**
     * Exposes the private LLM call for services in other modules.
     * Returns the raw string from the model. Throws RuntimeException on failure.
     */
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

    private <T> T parseResponse(String response, Class<T> clazz) {
        String cleaned = response.trim();
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
            return objectMapper.readValue(cleaned, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse LLM response: " + cleaned, e);
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