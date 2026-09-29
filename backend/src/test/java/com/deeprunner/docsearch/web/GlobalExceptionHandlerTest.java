package com.deeprunner.docsearch.web;

import com.deeprunner.docsearch.domain.dto.ProblemDetailsDto;
import com.deeprunner.docsearch.web.controller.GlobalExceptionHandler;
import com.deeprunner.docsearch.web.exception.RateLimitExceededException;
import com.deeprunner.docsearch.web.exception.ResourceNotFoundException;
import com.deeprunner.docsearch.web.exception.SearchUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException returns HTTP 405 Method Not Allowed")
    void testMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex =
            new HttpRequestMethodNotSupportedException("GET", List.of("POST"));

        ResponseEntity<ProblemDetailsDto> response = handler.handleMethodNotSupported(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().code());
        assertEquals(405, response.getBody().status());
        assertTrue(response.getBody().detail().contains("GET"));
    }

    @Test
    @DisplayName("ResourceNotFoundException returns HTTP 404 Not Found")
    void testResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Document not found");

        ResponseEntity<ProblemDetailsDto> response = handler.handleNotFound(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().code());
    }

    @Test
    @DisplayName("SearchUnavailableException returns HTTP 503 Service Unavailable with retry-after")
    void testSearchUnavailable() {
        SearchUnavailableException ex = new SearchUnavailableException("Cluster offline", 10);

        ResponseEntity<ProblemDetailsDto> response = handler.handleSearchUnavailable(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("SEARCH_UNAVAILABLE", response.getBody().code());
        assertEquals("10", response.getHeaders().getFirst("Retry-After"));
    }

    @Test
    @DisplayName("RateLimitExceededException returns HTTP 429 Too Many Requests")
    void testRateLimit() {
        RateLimitExceededException ex = new RateLimitExceededException("Rate limit reached", 1);

        ResponseEntity<ProblemDetailsDto> response = handler.handleRateLimit(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getBody().code());
        assertEquals("1", response.getHeaders().getFirst("Retry-After"));
    }

    @Test
    @DisplayName("Generic Exception returns HTTP 500 without leaking stack traces")
    void testGenericException() {
        Exception ex = new RuntimeException("Unexpected internal failure");

        ResponseEntity<ProblemDetailsDto> response = handler.handleGeneric(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().code());
        assertFalse(response.getBody().detail().contains("Unexpected internal failure"));
    }
}
