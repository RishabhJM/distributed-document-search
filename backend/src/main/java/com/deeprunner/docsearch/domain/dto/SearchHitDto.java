package com.deeprunner.docsearch.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchHitDto(
    UUID id,
    String externalId,
    String title,
    String snippet,
    double score,
    String author,
    List<String> tags,
    Instant createdAt
) {
}
