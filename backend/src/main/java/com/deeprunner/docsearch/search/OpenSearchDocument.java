package com.deeprunner.docsearch.search;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OpenSearchDocument(
    UUID id,
    String tenantId,
    String externalId,
    String title,
    String content,
    String author,
    List<String> tags,
    Instant createdAt,
    Instant updatedAt
) {
}
