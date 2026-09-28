package com.deeprunner.docsearch.domain.dto;

import java.util.List;

public record BulkIndexResponse(
    int total,
    int indexed,
    int pending,
    List<DocumentResponse> documents
) {
}
