package com.deeprunner.docsearch.web.exception;

public class RateLimitExceededException extends RuntimeException {
    private final int retryAfterSeconds;
    private final String tenantId;

    public RateLimitExceededException(String tenantId, int retryAfterSeconds) {
        super("Tenant '" + tenantId + "' exceeded allowed requests/second.");
        this.tenantId = tenantId;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getTenantId() {
        return tenantId;
    }
}
