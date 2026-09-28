package com.deeprunner.docsearch.domain.dto;

import java.util.List;
import java.util.Map;

public record SearchResultDto(
    String query,
    String tenantId,
    List<SearchHitDto> hits,
    PageInfoDto page,
    long tookMs,
    boolean cached,
    Map<String, Map<String, Long>> facets
) {
}
