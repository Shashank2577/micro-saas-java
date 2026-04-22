package com.changelog.ai;

import com.changelog.dto.AiDuplicateCheckResponse;
import com.changelog.dto.AiPriorityResponse;
import com.changelog.dto.AiRewriteResponse;
import com.changelog.dto.AiTitleResponse;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private LiteLlmApi liteLlmApi;

    @InjectMocks
    private AiService aiService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiService, "model", "gpt-4");
    }

    private ChatCompletionResponse mockLlmResponse(String content) {
        ChatCompletionResponse response = new ChatCompletionResponse();
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        ChatCompletionResponse.Message message = new ChatCompletionResponse.Message();
        message.setContent(content);
        choice.setMessage(message);
        response.setChoices(List.of(choice));
        return response;
    }

    @Test
    void testRewrite_returnsRewrittenContent() throws IOException {
        Call<ChatCompletionResponse> mockCall = mock(Call.class);
        when(liteLlmApi.chatCompletions(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(mockLlmResponse("Polished release notes content")));

        AiRewriteResponse result = aiService.rewrite("raw notes", "professional");

        assertThat(result.getRewritten()).isEqualTo("Polished release notes content");
    }

    @Test
    void testGenerateTitles_parsesJsonArray() throws IOException {
        Call<ChatCompletionResponse> mockCall = mock(Call.class);
        when(liteLlmApi.chatCompletions(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(mockLlmResponse("[\"Title One\", \"Title Two\", \"Title Three\"]")));

        AiTitleResponse result = aiService.generateTitles("some content");

        assertThat(result.getTitles()).hasSize(3).contains("Title One");
    }

    @Test
    void testGenerateTitles_stripsMarkdownFences() throws IOException {
        Call<ChatCompletionResponse> mockCall = mock(Call.class);
        when(liteLlmApi.chatCompletions(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(mockLlmResponse("```json\n[\"T1\",\"T2\",\"T3\"]\n```")));

        AiTitleResponse result = aiService.generateTitles("content");

        assertThat(result.getTitles()).hasSize(3).containsExactly("T1", "T2", "T3");
    }

    @Test
    void testGenerateTitles_throwsWhenJsonMalformed() throws IOException {
        Call<ChatCompletionResponse> mockCall = mock(Call.class);
        when(liteLlmApi.chatCompletions(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(mockLlmResponse("not valid json")));

        assertThatThrownBy(() -> aiService.generateTitles("content"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse titles from LLM response");
    }

    @Test
    void testCheckDuplicateIssue_returnsDuplicate() throws IOException {
        Call<ChatCompletionResponse> mockCall = mock(Call.class);
        when(liteLlmApi.chatCompletions(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(mockLlmResponse("{\"duplicate\": true, \"reason\": \"Login bug\", \"confidenceScore\": 0.92}")));

        AiDuplicateCheckResponse result = aiService.checkDuplicateIssue("Can't login", "Users can't log in", List.of("Login bug", "Signup issue"));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getReason()).isEqualTo("Login bug");
        assertThat(result.getConfidenceScore()).isEqualTo(0.92);
    }

    @Test
    void testCheckDuplicateIssue_returnsFallbackOnException() throws IOException {
        Call<ChatCompletionResponse> mockCall = mock(Call.class);
        when(liteLlmApi.chatCompletions(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("Network error"));

        AiDuplicateCheckResponse result = aiService.checkDuplicateIssue("title", "desc", List.of());

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getConfidenceScore()).isEqualTo(0.0);
    }

    @Test
    void testSuggestIssuePriority_returnsHighPriority() throws IOException {
        Call<ChatCompletionResponse> mockCall = mock(Call.class);
        when(liteLlmApi.chatCompletions(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(mockLlmResponse("{\"priority\": \"high\", \"reason\": \"Production impact\"}")));

        AiPriorityResponse result = aiService.suggestIssuePriority("App crash on login", "500 error on production");

        assertThat(result.getPriority()).isEqualTo("high");
        assertThat(result.getReason()).isEqualTo("Production impact");
    }

    @Test
    void testSuggestIssuePriority_returnsMediumFallbackOnException() throws IOException {
        Call<ChatCompletionResponse> mockCall = mock(Call.class);
        when(liteLlmApi.chatCompletions(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new RuntimeException("LLM call failed: 503"));

        AiPriorityResponse result = aiService.suggestIssuePriority("title", "desc");

        assertThat(result.getPriority()).isEqualTo("medium");
    }

    @Test
    void testCallLlmRaw_returnsRawString() throws IOException {
        Call<ChatCompletionResponse> mockCall = mock(Call.class);
        when(liteLlmApi.chatCompletions(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.success(mockLlmResponse("raw llm output")));

        String result = aiService.callLlmRaw("my prompt");

        assertThat(result).isEqualTo("raw llm output");
    }

    @Test
    void testCallLlmRaw_throwsOnFailedResponse() throws IOException {
        Call<ChatCompletionResponse> mockCall = mock(Call.class);
        when(liteLlmApi.chatCompletions(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(Response.error(503, ResponseBody.create(null, "")));

        assertThatThrownBy(() -> aiService.callLlmRaw("prompt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM call failed");
    }
}
