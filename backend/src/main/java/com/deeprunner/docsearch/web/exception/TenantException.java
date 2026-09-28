package com.deeprunner.docsearch.web.exception;

public class TenantException extends RuntimeException {
    private final int statusCode;
    private final String code;

    public TenantException(String message, int statusCode, String code) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code;
    }
}
