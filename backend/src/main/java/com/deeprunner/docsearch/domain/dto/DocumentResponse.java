package com.deeprunner.docsearch.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    String tenantId,
    String externalId,
    String title,
    String content,
    String author,
    List<String> tags,
    String contentType,
    Long version,
    IndexingState indexingState,
    Instant createdAt,
    Instant updatedAt
) {
}
