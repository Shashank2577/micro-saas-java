package com.changelog.ai;

import com.changelog.dto.AiRewriteRequest;
import com.changelog.dto.AiRewriteResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@WireMockTest(httpPort = 4000)
@ActiveProfiles("local")
class AiServiceIntegrationTest {

    @Autowired
    private AiService aiService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("ai.gateway-url", () -> "http://localhost:4000");
    }

    @Test
    void shouldRewriteContentUsingAi() {
        // Stub AI Gateway
        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"choices\":[{\"message\":{\"content\":\"Rewritten content\"}}]}")));

        AiRewriteRequest request = new AiRewriteRequest();
        request.setContent("Original content");
        request.setTone("professional");

        AiRewriteResponse response = aiService.rewrite(request.getContent(), request.getTone());

        assertNotNull(response);
        assertEquals("Rewritten content", response.getRewritten());
        
        verify(postRequestedFor(urlEqualTo("/chat/completions"))
                .withRequestBody(containing("Original content")));
    }

    @Test
    void shouldHandleAiErrorGracefully() {
        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(500)));

        AiRewriteRequest request = new AiRewriteRequest();
        request.setContent("Original content");
        request.setTone("professional");

        assertThrows(Exception.class, () -> aiService.rewrite(request.getContent(), request.getTone()));
    }
}
