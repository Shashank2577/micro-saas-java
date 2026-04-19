package com.changelog.api;

import com.changelog.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private UUID testTenantId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
    }

    @Test
    void testGenerateToken() throws Exception {
        String token = jwtTokenProvider.generateToken(testTenantId, testUserId, "test@example.com");

        assert token != null : "JWT token should not be null";
        assert !token.isEmpty() : "JWT token should not be empty";
        assert token.contains(".") : "JWT token should contain 3 parts separated by dots";
    }

    @Test
    void testValidateToken() throws Exception {
        String token = jwtTokenProvider.generateToken(testTenantId, testUserId, "test@example.com");
        boolean isValid = jwtTokenProvider.validateToken(token);

        assert isValid : "Valid JWT token should pass validation";
    }

    @Test
    void testExtractTenantIdFromToken() throws Exception {
        String token = jwtTokenProvider.generateToken(testTenantId, testUserId, "test@example.com");
        UUID extractedTenantId = UUID.fromString(jwtTokenProvider.getTenantId(token));

        assert extractedTenantId.equals(testTenantId) : "Extracted tenant ID should match original";
    }
}