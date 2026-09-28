package com.deeprunner.docsearch.service;

import com.deeprunner.docsearch.cache.CacheKeys;
import com.deeprunner.docsearch.domain.dto.*;
import com.deeprunner.docsearch.domain.entity.DocumentEntity;
import com.deeprunner.docsearch.domain.entity.OutboxEventEntity;
import com.deeprunner.docsearch.repository.DocumentRepository;
import com.deeprunner.docsearch.repository.OutboxEventRepository;
import com.deeprunner.docsearch.search.OpenSearchAdapter;
import com.deeprunner.docsearch.search.OpenSearchDocument;
import com.deeprunner.docsearch.web.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OpenSearchAdapter openSearchAdapter;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter indexFailureCounter;

    public DocumentService(DocumentRepository documentRepository,
                           OutboxEventRepository outboxEventRepository,
                           OpenSearchAdapter openSearchAdapter,
                           StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry) {
        this.documentRepository = documentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.openSearchAdapter = openSearchAdapter;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.indexFailureCounter = meterRegistry.counter("dr_index_post_commit_failures_total");
    }

    @Transactional
    public DocumentWriteRecord createDocumentInTx(String tenantId, DocumentRequest request) {
        UUID docId = UUID.randomUUID();
        Instant now = Instant.now();

        DocumentEntity entity = new DocumentEntity();
        entity.setId(docId);
        entity.setTenantId(tenantId);
        entity.setExternalId(request.externalId());
        entity.setTitle(request.title());
        entity.setContent(request.content());
        entity.setAuthor(request.author());
        entity.setTags(request.tags() != null ? request.tags().toArray(new String[0]) : new String[0]);
        entity.setContentType(request.contentType());
        entity.setVersion(0L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        DocumentEntity saved = documentRepository.save(entity);

        // Record outbox event atomically
        OpenSearchDocument openSearchDoc = toOpenSearchDoc(saved);
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(openSearchDoc);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }

        OutboxEventEntity outboxEvent = new OutboxEventEntity(
            tenantId, docId, OutboxEventEntity.EVENT_INDEX, payloadJson
        );
        OutboxEventEntity savedOutbox = outboxEventRepository.save(outboxEvent);

        return new DocumentWriteRecord(saved, savedOutbox.getId(), openSearchDoc);
    }

    public DocumentResponse indexDocument(String tenantId, DocumentRequest request) {
        DocumentWriteRecord writeRecord = createDocumentInTx(tenantId, request);
        DocumentEntity saved = writeRecord.entity();
        Long outboxId = writeRecord.outboxId();
        OpenSearchDocument searchDoc = writeRecord.searchDoc();

        IndexingState state = IndexingState.PENDING;
        try {
            boolean indexed = openSearchAdapter.indexDocument(searchDoc);
            if (indexed) {
                state = IndexingState.INDEXED;
                markOutboxProcessed(outboxId);
                evictCaches(tenantId, saved.getId());
            } else {
                indexFailureCounter.increment();
                log.warn("OpenSearch direct index returned non-200 for doc {}. Leaving in outbox.", saved.getId());
            }
        } catch (Exception e) {
            indexFailureCounter.increment();
            log.warn("OpenSearch direct index failed for doc {} ({}). Outbox will reconcile.", saved.getId(), e.getMessage());
        }

        return toResponse(saved, state);
    }

    public DocumentResponse getDocument(String tenantId, UUID id) {
        String cacheKey = CacheKeys.documentKey(tenantId, id);

        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                return objectMapper.readValue(cachedJson, DocumentResponse.class);
            }
        } catch (Exception e) {
            log.debug("Redis cache read failed for doc {} ({}): falling back to DB", id, e.getMessage());
        }

        DocumentEntity entity = documentRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Document " + id + " not found for tenant " + tenantId));

        DocumentResponse response = toResponse(entity, IndexingState.INDEXED);

        try {
            redisTemplate.opsForValue().set(
                cacheKey,
                objectMapper.writeValueAsString(response),
                Duration.ofMinutes(10)
            );
        } catch (Exception e) {
            log.debug("Redis cache write failed for doc {}: {}", id, e.getMessage());
        }

        return response;
    }

    @Transactional
    public void deleteDocument(String tenantId, UUID id) {
        DocumentEntity entity = documentRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Document " + id + " not found for tenant " + tenantId));

        // Fail-closed order: delete from search index first
        try {
            openSearchAdapter.deleteDocument(tenantId, id);
        } catch (Exception e) {
            log.warn("Direct OpenSearch delete failed for doc {}: {}", id, e.getMessage());
        }

        entity.setDeletedAt(Instant.now());
        documentRepository.save(entity);

        OutboxEventEntity outboxEvent = new OutboxEventEntity(
            tenantId, id, OutboxEventEntity.EVENT_DELETE, "{}"
        );
        outboxEventRepository.save(outboxEvent);

        evictCaches(tenantId, id);
    }

    public BulkIndexResponse bulkIndex(String tenantId, BulkIndexRequest request) {
        List<DocumentResponse> responses = new ArrayList<>();
        List<OpenSearchDocument> searchDocs = new ArrayList<>();
        List<Long> outboxIds = new ArrayList<>();

        for (DocumentRequest req : request.documents()) {
            DocumentWriteRecord rec = createDocumentInTx(tenantId, req);
            searchDocs.add(rec.searchDoc());
            outboxIds.add(rec.outboxId());
            responses.add(toResponse(rec.entity(), IndexingState.PENDING));
        }

        int indexedCount = 0;
        try {
            boolean bulkSuccess = openSearchAdapter.bulkIndexDocuments(searchDocs);
            if (bulkSuccess) {
                for (Long outboxId : outboxIds) {
                    markOutboxProcessed(outboxId);
                }
                for (DocumentResponse r : responses) {
                    evictCaches(tenantId, r.id());
                }
                indexedCount = responses.size();
                responses = responses.stream()
                    .map(r -> new DocumentResponse(
                        r.id(), r.tenantId(), r.externalId(), r.title(), r.content(),
                        r.author(), r.tags(), r.contentType(), r.version(),
                        IndexingState.INDEXED, r.createdAt(), r.updatedAt()))
                    .toList();
            }
        } catch (Exception e) {
            log.warn("Bulk index direct push failed ({}); outbox relay will reconcile", e.getMessage());
        }

        int pendingCount = responses.size() - indexedCount;
        return new BulkIndexResponse(responses.size(), indexedCount, pendingCount, responses);
    }

    private void markOutboxProcessed(Long outboxId) {
        if (outboxId == null) return;
        try {
            outboxEventRepository.findById(outboxId).ifPresent(event -> {
                event.setStatus(OutboxEventEntity.STATUS_PROCESSED);
                event.setUpdatedAt(Instant.now());
                outboxEventRepository.save(event);
            });
        } catch (Exception e) {
            log.warn("Failed to mark outbox row {} processed: {}", outboxId, e.getMessage());
        }
    }

    private void evictCaches(String tenantId, UUID documentId) {
        try {
            // Evict document cache
            redisTemplate.delete(CacheKeys.documentKey(tenantId, documentId));
            // O(1) Search cache invalidation via generation bump
            redisTemplate.opsForValue().increment(CacheKeys.searchGenKey(tenantId));
        } catch (Exception e) {
            log.debug("Redis cache eviction skipped: {}", e.getMessage());
        }
    }

    private OpenSearchDocument toOpenSearchDoc(DocumentEntity entity) {
        List<String> tagList = entity.getTags() != null ? Arrays.asList(entity.getTags()) : Collections.emptyList();
        return new OpenSearchDocument(
            entity.getId(),
            entity.getTenantId(),
            entity.getExternalId(),
            entity.getTitle(),
            entity.getContent(),
            entity.getAuthor(),
            tagList,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private DocumentResponse toResponse(DocumentEntity entity, IndexingState state) {
        List<String> tagList = entity.getTags() != null ? Arrays.asList(entity.getTags()) : Collections.emptyList();
        return new DocumentResponse(
            entity.getId(),
            entity.getTenantId(),
            entity.getExternalId(),
            entity.getTitle(),
            entity.getContent(),
            entity.getAuthor(),
            tagList,
            entity.getContentType(),
            entity.getVersion(),
            state,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public record DocumentWriteRecord(
        DocumentEntity entity,
        Long outboxId,
        OpenSearchDocument searchDoc
    ) {}
}
