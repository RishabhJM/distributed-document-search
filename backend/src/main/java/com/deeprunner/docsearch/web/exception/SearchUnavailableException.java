package com.deeprunner.docsearch.web.exception;

public class SearchUnavailableException extends RuntimeException {
    private final int retryAfterSeconds;

    public SearchUnavailableException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public SearchUnavailableException(String message, Throwable cause, int retryAfterSeconds) {
        super(message, cause);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
