package com.deeprunner.docsearch.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetailsDto(
    String type,
    String title,
    int status,
    String detail,
    String code,
    Integer retryAfterSeconds,
    String requestId,
    Instant timestamp
) {
    public static ProblemDetailsDto of(String typeSlug, String title, int status, String detail, String code, String requestId) {
        return new ProblemDetailsDto(
            "https://docsearch.deeprunner.com/errors/" + typeSlug,
            title,
            status,
            detail,
            code,
            null,
            requestId,
            Instant.now()
        );
    }

    public static ProblemDetailsDto rateLimit(int retryAfterSeconds, String detail, String requestId) {
        return new ProblemDetailsDto(
            "https://docsearch.deeprunner.com/errors/rate-limit-exceeded",
            "Rate limit exceeded",
            429,
            detail,
            "RATE_LIMIT_EXCEEDED",
            retryAfterSeconds,
            requestId,
            Instant.now()
        );
    }
}
