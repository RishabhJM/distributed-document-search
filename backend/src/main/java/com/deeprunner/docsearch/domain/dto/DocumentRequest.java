package com.deeprunner.docsearch.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DocumentRequest(
    @Size(max = 256, message = "externalId must not exceed 256 characters")
    String externalId,

    @NotBlank(message = "title is required")
    @Size(max = 512, message = "title must not exceed 512 characters")
    String title,

    @NotBlank(message = "content is required")
    @Size(max = 1048576, message = "content must not exceed 1 MB")
    String content,

    @Size(max = 256, message = "author must not exceed 256 characters")
    String author,

    List<String> tags,

    @Size(max = 64, message = "contentType must not exceed 64 characters")
    String contentType
) {
    public DocumentRequest {
        if (contentType == null || contentType.isBlank()) {
            contentType = "text/plain";
        }
    }
}
