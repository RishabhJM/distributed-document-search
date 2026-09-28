package com.deeprunner.docsearch.web.controller;

import com.deeprunner.docsearch.context.TenantContext;
import com.deeprunner.docsearch.domain.dto.SearchResultDto;
import com.deeprunner.docsearch.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping({"/search", "/api/v1/search"})
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<SearchResultDto> search(
        @RequestParam(name = "q", defaultValue = "") String query,
        @RequestParam(name = "tenant", required = false) String tenantParam,
        @RequestParam(name = "from", defaultValue = "0") int from,
        @RequestParam(name = "size", defaultValue = "20") int size,
        @RequestParam(name = "tags", required = false) String tagsParam,
        @RequestParam(name = "highlight", defaultValue = "true") boolean highlight,
        @RequestParam(name = "fuzzy", defaultValue = "false") boolean fuzzy
    ) {
        String tenantId = TenantContext.requireTenantId();

        List<String> tags = null;
        if (tagsParam != null && !tagsParam.isBlank()) {
            tags = Arrays.stream(tagsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        }

        SearchResultDto result = searchService.search(
            tenantId, query, from, size, tags, highlight, fuzzy
        );

        return ResponseEntity.ok(result);
    }
}
