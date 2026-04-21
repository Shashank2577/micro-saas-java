package com.changelog.ai;

import lombok.Data;
import java.util.List;

@Data
public class EmbeddingResponse {
    private List<EmbeddingData> data;

    @Data
    public static class EmbeddingData {
        private float[] embedding;
    }
}
