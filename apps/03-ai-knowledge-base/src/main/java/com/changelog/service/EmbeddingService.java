package com.changelog.service;

import com.changelog.ai.EmbeddingRequest;
import com.changelog.ai.EmbeddingResponse;
import com.changelog.ai.LiteLlmApi;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final LiteLlmApi liteLlmApi;

    @Value("${ai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(embeddingModel)
                    .input(text)
                    .build();

            var call = liteLlmApi.createEmbedding(request);
            var response = call.execute();

            if (response.isSuccessful() && response.body() != null && !response.body().getData().isEmpty()) {
                return response.body().getData().get(0).getEmbedding();
            } else {
                log.error("Failed to generate embedding: HTTP {} - {}", response.code(), response.message());
                return null;
            }
        } catch (IOException e) {
            log.error("Failed to call AI gateway for embeddings: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("Unexpected error calling AI gateway for embeddings: {}", e.getMessage(), e);
            return null;
        }
    }

    public PGvector toPGvector(float[] embedding) {
        if (embedding == null) {
            return null;
        }
        return new PGvector(embedding);
    }
}
