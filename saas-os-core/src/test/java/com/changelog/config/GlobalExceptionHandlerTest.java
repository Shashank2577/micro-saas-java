package com.changelog.config;

import com.changelog.dto.ErrorResponse;
import com.changelog.exception.EntityNotFoundException;
import com.changelog.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest webRequest = mock(WebRequest.class);

    public GlobalExceptionHandlerTest() {
        when(webRequest.getDescription(false)).thenReturn("uri=/test");
    }

    @Test
    void testHandleEntityNotFound_returns404() {
        EntityNotFoundException ex = new EntityNotFoundException("Board not found: 123");
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getDetail()).contains("Board not found: 123");
        assertThat(response.getBody().getTitle()).isEqualTo("Resource Not Found");
    }

    @Test
    void testHandleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid UUID format");
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getDetail()).contains("Invalid UUID format");
        assertThat(response.getBody().getTitle()).isEqualTo("Bad Request");
    }

    @Test
    void testHandleForbidden_returns403() {
        ForbiddenException ex = new ForbiddenException("Access denied");
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getDetail()).contains("Access denied");
        assertThat(response.getBody().getTitle()).isEqualTo("Forbidden");
    }

    @Test
    void testHandleIllegalState_returns409() {
        IllegalStateException ex = new IllegalStateException("Workflow is not active");
        ResponseEntity<ErrorResponse> response = handler.handleIllegalState(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getDetail()).contains("Workflow is not active");
        assertThat(response.getBody().getTitle()).isEqualTo("Conflict");
    }

    @Test
    void testHandleGenericException_returns500() {
        Exception ex = new RuntimeException("unexpected");
        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getDetail()).contains("unexpected");
        assertThat(response.getBody().getTitle()).isEqualTo("Internal Server Error");
    }
}
