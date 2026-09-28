package com.deeprunner.docsearch.domain.dto;

public record PageInfoDto(
    int from,
    int size,
    long totalHits,
    boolean totalIsLowerBound
) {
}
