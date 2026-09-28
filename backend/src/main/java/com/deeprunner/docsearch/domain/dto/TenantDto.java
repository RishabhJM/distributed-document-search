package com.deeprunner.docsearch.domain.dto;

public record TenantDto(
    String id,
    String name,
    String tier,
    int rateLimitPerSecond,
    boolean active
) {
}
