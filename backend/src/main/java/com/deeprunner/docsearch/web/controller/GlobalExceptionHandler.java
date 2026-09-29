package com.deeprunner.docsearch.web.controller;

import com.deeprunner.docsearch.context.AppRequestContextFilter;
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
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetailsDto> handleNotFound(ResourceNotFoundException ex) {
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
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
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
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
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
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
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
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
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetailsDto> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
        log.warn("Method not allowed for request {}: {}", requestId, ex.getMessage());
        ProblemDetailsDto problem = ProblemDetailsDto.of(
            "method-not-allowed",
            "Method Not Allowed",
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            ex.getMessage(),
            "METHOD_NOT_ALLOWED",
            requestId
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetailsDto> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
        log.warn("Media type not supported for request {}: {}", requestId, ex.getMessage());
        ProblemDetailsDto problem = ProblemDetailsDto.of(
            "unsupported-media-type",
            "Unsupported Media Type",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
            ex.getMessage(),
            "UNSUPPORTED_MEDIA_TYPE",
            requestId
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetailsDto> handleNoResourceFound(NoResourceFoundException ex) {
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetailsDto> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
        String detail = String.format("Parameter '%s' should be of type '%s'",
            ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        ProblemDetailsDto problem = ProblemDetailsDto.of(
            "invalid-request",
            "Bad Request",
            HttpStatus.BAD_REQUEST.value(),
            detail,
            "INVALID_REQUEST",
            requestId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetailsDto> handleMissingParam(MissingServletRequestParameterException ex) {
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
        ProblemDetailsDto problem = ProblemDetailsDto.of(
            "invalid-request",
            "Bad Request",
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage(),
            "INVALID_REQUEST",
            requestId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailsDto> handleGeneric(Exception ex) {
        String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
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
