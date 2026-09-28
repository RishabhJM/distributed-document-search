package com.deeprunner.docsearch.web.controller;

import com.deeprunner.docsearch.context.RequestContextFilter;
import com.deeprunner.docsearch.domain.dto.ProblemDetailsDto;
import com.deeprunner.docsearch.web.exception.RateLimitExceededException;
import com.deeprunner.docsearch.web.exception.ResourceNotFoundException;
import com.deeprunner.docsearch.web.exception.SearchUnavailableException;
import com.deeprunner.docsearch.web.exception.TenantException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetailsDto> handleNotFound(ResourceNotFoundException ex) {
        String requestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID_KEY);
        ProblemDetailsDto problem = ProblemDetailsDto.of(
            "not-found",
            "Resource Not Found",
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            "RESOURCE_NOT_FOUND",
            requestId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    @ExceptionHandler(TenantException.class)
    public ResponseEntity<ProblemDetailsDto> handleTenantException(TenantException ex) {
        String requestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID_KEY);
        ProblemDetailsDto problem = ProblemDetailsDto.of(
            "tenant-error",
            "Tenant Authorization Error",
            ex.getStatusCode(),
            ex.getMessage(),
            ex.getCode(),
            requestId
        );
        return ResponseEntity.status(ex.getStatusCode())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetailsDto> handleRateLimit(RateLimitExceededException ex) {
        String requestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID_KEY);
        ProblemDetailsDto problem = ProblemDetailsDto.rateLimit(
            ex.getRetryAfterSeconds(),
            ex.getMessage(),
            requestId
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    @ExceptionHandler(SearchUnavailableException.class)
    public ResponseEntity<ProblemDetailsDto> handleSearchUnavailable(SearchUnavailableException ex) {
        String requestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID_KEY);
        ProblemDetailsDto problem = new ProblemDetailsDto(
            "https://docsearch.deeprunner.com/errors/search-unavailable",
            "Search Service Unavailable",
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            ex.getMessage(),
            "SEARCH_UNAVAILABLE",
            ex.getRetryAfterSeconds(),
            requestId,
            java.time.Instant.now()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailsDto> handleValidation(MethodArgumentNotValidException ex) {
        String requestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID_KEY);
        String validationErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));

        ProblemDetailsDto problem = ProblemDetailsDto.of(
            "invalid-request",
            "Bad Request",
            HttpStatus.BAD_REQUEST.value(),
            validationErrors,
            "INVALID_REQUEST",
            requestId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailsDto> handleGeneric(Exception ex) {
        String requestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID_KEY);
        log.error("Unhandled exception for request {}", requestId, ex);

        // Security: NEVER expose stack traces or internal implementation details
        ProblemDetailsDto problem = ProblemDetailsDto.of(
            "internal-server-error",
            "Internal Server Error",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred. Please reference requestId: " + requestId,
            "INTERNAL_SERVER_ERROR",
            requestId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }
}
