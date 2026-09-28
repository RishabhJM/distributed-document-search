package com.deeprunner.docsearch.web.controller;

import com.deeprunner.docsearch.context.TenantContext;
import com.deeprunner.docsearch.domain.dto.BulkIndexRequest;
import com.deeprunner.docsearch.domain.dto.BulkIndexResponse;
import com.deeprunner.docsearch.domain.dto.DocumentRequest;
import com.deeprunner.docsearch.domain.dto.DocumentResponse;
import com.deeprunner.docsearch.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping({"/documents", "/api/v1/documents"})
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> indexDocument(@Valid @RequestBody DocumentRequest request,
                                                          UriComponentsBuilder uriBuilder) {
        String tenantId = TenantContext.requireTenantId();
        DocumentResponse response = documentService.indexDocument(tenantId, request);
        URI location = uriBuilder.path("/documents/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/_bulk")
    public ResponseEntity<BulkIndexResponse> bulkIndex(@Valid @RequestBody BulkIndexRequest request) {
        String tenantId = TenantContext.requireTenantId();
        BulkIndexResponse response = documentService.bulkIndex(tenantId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable("id") UUID id) {
        String tenantId = TenantContext.requireTenantId();
        DocumentResponse response = documentService.getDocument(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable("id") UUID id) {
        String tenantId = TenantContext.requireTenantId();
        documentService.deleteDocument(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
