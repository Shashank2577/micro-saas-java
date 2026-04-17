package com.changelog.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
public class LiteLlmClient {

    @Value("${ai.gateway-url}")
    private String gatewayUrl;

    @Bean
    public LiteLlmApi liteLlmApi() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(gatewayUrl + "/")
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
        return retrofit.create(LiteLlmApi.class);
    }
}
