package com.deeprunner.docsearch.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkIndexRequest(
    @NotEmpty(message = "documents list must not be empty")
    @Size(max = 1000, message = "Bulk index is capped at 1000 documents per batch")
    @Valid
    List<DocumentRequest> documents
) {
}
