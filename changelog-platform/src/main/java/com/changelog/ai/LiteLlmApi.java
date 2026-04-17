package com.changelog.ai;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface LiteLlmApi {
    @POST("/chat/completions")
    Call<ChatCompletionResponse> chatCompletions(@Body ChatCompletionRequest request);
}
