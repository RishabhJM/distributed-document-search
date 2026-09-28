package com.deeprunner.docsearch.service;

import com.deeprunner.docsearch.domain.entity.OutboxEventEntity;
import com.deeprunner.docsearch.repository.OutboxEventRepository;
import com.deeprunner.docsearch.search.OpenSearchAdapter;
import com.deeprunner.docsearch.search.OpenSearchDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;

@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final OpenSearchAdapter openSearchAdapter;
    private final ObjectMapper objectMapper;
    private final Counter outboxDeadCounter;
    private final MeterRegistry meterRegistry;

    public OutboxRelayService(OutboxEventRepository outboxEventRepository,
                              OpenSearchAdapter openSearchAdapter,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.openSearchAdapter = openSearchAdapter;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.outboxDeadCounter = meterRegistry.counter("dr_outbox_dead_total");
    }

    @PostConstruct
    public void registerMetrics() {
        Gauge.builder("dr_outbox_pending_rows", outboxEventRepository,
            repo -> repo.countByStatus(OutboxEventEntity.STATUS_PENDING))
            .description("Total number of pending outbox events awaiting indexing")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${docsearch.outbox.poll-interval-ms:2000}")
    @Transactional
    public void drainOutbox() {
        List<OutboxEventEntity> pendingEvents;
        try {
            pendingEvents = outboxEventRepository.findPendingForProcessingNative(200);
        } catch (Exception e) {
            // Fallback for non-Postgres environments (e.g. H2 integration tests)
            pendingEvents = outboxEventRepository.findTop200ByStatusOrderByIdAsc(OutboxEventEntity.STATUS_PENDING);
        }

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Outbox relay processing {} pending events", pendingEvents.size());

        for (OutboxEventEntity event : pendingEvents) {
            try {
                processEvent(event);
            } catch (Exception e) {
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(e.getMessage());
                event.setUpdatedAt(Instant.now());

                if (attempts >= MAX_ATTEMPTS) {
                    event.setStatus(OutboxEventEntity.STATUS_DEAD);
                    outboxDeadCounter.increment();
                    log.error("Outbox event {} reached MAX_ATTEMPTS ({}) - marked DEAD: {}",
                        event.getId(), MAX_ATTEMPTS, e.getMessage());
                } else {
                    log.warn("Outbox event {} failed attempt {}/{}: {}",
                        event.getId(), attempts, MAX_ATTEMPTS, e.getMessage());
                }
                outboxEventRepository.save(event);
            }
        }
    }

    private void processEvent(OutboxEventEntity event) throws Exception {
        if (OutboxEventEntity.EVENT_INDEX.equals(event.getEventType())) {
            OpenSearchDocument doc = objectMapper.readValue(event.getPayload(), OpenSearchDocument.class);
            boolean success = openSearchAdapter.indexDocument(doc);
            if (success) {
                event.setStatus(OutboxEventEntity.STATUS_PROCESSED);
                event.setUpdatedAt(Instant.now());
                outboxEventRepository.save(event);
            } else {
                throw new RuntimeException("OpenSearch index returned non-200");
            }
        } else if (OutboxEventEntity.EVENT_DELETE.equals(event.getEventType())) {
            boolean success = openSearchAdapter.deleteDocument(event.getTenantId(), event.getDocumentId());
            if (success) {
                event.setStatus(OutboxEventEntity.STATUS_PROCESSED);
                event.setUpdatedAt(Instant.now());
                outboxEventRepository.save(event);
            } else {
                throw new RuntimeException("OpenSearch delete returned non-200");
            }
        }
    }
}
