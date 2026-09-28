package com.deeprunner.docsearch.integration;

import com.deeprunner.docsearch.cache.CacheKeys;
import com.deeprunner.docsearch.domain.dto.DocumentRequest;
import com.deeprunner.docsearch.domain.dto.DocumentResponse;
import com.deeprunner.docsearch.domain.entity.TenantEntity;
import com.deeprunner.docsearch.repository.DocumentRepository;
import com.deeprunner.docsearch.repository.OutboxEventRepository;
import com.deeprunner.docsearch.repository.TenantRepository;
import com.deeprunner.docsearch.search.OpenSearchAdapter;
import com.deeprunner.docsearch.service.DocumentService;
import com.deeprunner.docsearch.web.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@EntityScan("com.deeprunner.docsearch.domain.entity")
@ActiveProfiles("test")
class TenantIsolationIT {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private DocumentService documentService;
    private OpenSearchAdapter mockOpenSearch;
    private StringRedisTemplate mockRedis;

    @BeforeEach
    void setUp() throws Exception {
        tenantRepository.save(new TenantEntity("acme", "Acme Corp", "ENTERPRISE", 50, "ACTIVE"));
        tenantRepository.save(new TenantEntity("globex", "Globex Corp", "ENTERPRISE", 100, "ACTIVE"));

        mockOpenSearch = mock(OpenSearchAdapter.class);
        when(mockOpenSearch.indexDocument(any())).thenReturn(true);
        when(mockOpenSearch.deleteDocument(any(), any())).thenReturn(true);

        mockRedis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        documentService = new DocumentService(
            documentRepository,
            outboxEventRepository,
            mockOpenSearch,
            mockRedis,
            mapper,
            new SimpleMeterRegistry()
        );
    }

    @Test
    @DisplayName("Case 1: Document created by Acme cannot be retrieved by Globex (returns 404, not 403)")
    void testCrossTenantReadReturns404() {
        DocumentRequest req = new DocumentRequest(
            "ext-1", "Acme Secret Strategy", "Confidential details", "alice", List.of("strategy"), "text/plain"
        );

        DocumentResponse created = documentService.indexDocument("acme", req);
        assertNotNull(created.id());

        // Acme can read its own document
        DocumentResponse acmeRead = documentService.getDocument("acme", created.id());
        assertEquals("Acme Secret Strategy", acmeRead.title());

        // Globex attempting to read Acme doc gets 404
        assertThrows(ResourceNotFoundException.class, () ->
            documentService.getDocument("globex", created.id()),
            "Attempting to read another tenant's document must throw 404 not found"
        );
    }

    @Test
    @DisplayName("Case 2: Globex cannot delete an Acme document")
    void testCrossTenantDeleteFails() {
        DocumentRequest req = new DocumentRequest(
            "ext-2", "Payroll Runbook", "Steps for payroll", "bob", List.of("payroll"), "text/plain"
        );
        DocumentResponse created = documentService.indexDocument("acme", req);

        assertThrows(ResourceNotFoundException.class, () ->
            documentService.deleteDocument("globex", created.id()),
            "Cross-tenant deletion must fail with 404"
        );

        // Document remains intact for Acme
        DocumentResponse intact = documentService.getDocument("acme", created.id());
        assertNotNull(intact);
    }

    @Test
    @DisplayName("Case 3: Cache keys strictly segregate tenants to prevent cross-tenant cache leaks")
    void testCacheKeySegregation() {
        UUID docId = UUID.randomUUID();
        String acmeKey = CacheKeys.documentKey("acme", docId);
        String globexKey = CacheKeys.documentKey("globex", docId);

        assertNotEquals(acmeKey, globexKey);
        assertTrue(acmeKey.contains(":acme:"));
        assertTrue(globexKey.contains(":globex:"));
    }

    @Test
    @DisplayName("Case 4: Search cache keys are separated by tenant and generation")
    void testSearchCacheKeySegregation() {
        String queryHash = CacheKeys.canonicalQueryHash("revenue", "finance", false, 0, 10);
        String keyAcme = CacheKeys.searchKey("acme", 1L, queryHash);
        String keyGlobex = CacheKeys.searchKey("globex", 1L, queryHash);

        assertNotEquals(keyAcme, keyGlobex);
        assertTrue(keyAcme.startsWith("search:v1:acme:1:"));
        assertTrue(keyGlobex.startsWith("search:v1:globex:1:"));
    }

    @Test
    @DisplayName("Case 5: Outbox event captures correct tenantId and document ID")
    void testOutboxEventTenantIsolation() {
        DocumentRequest req = new DocumentRequest(
            "ext-3", "Q3 Report", "Report content", "carol", List.of("report"), "text/plain"
        );
        DocumentResponse created = documentService.indexDocument("acme", req);

        var events = outboxEventRepository.findAll();
        assertFalse(events.isEmpty());
        var event = events.stream()
            .filter(e -> e.getDocumentId().equals(created.id()))
            .findFirst()
            .orElseThrow();

        assertEquals("acme", event.getTenantId());
    }
}
