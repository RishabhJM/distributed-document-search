package com.deeprunner.docsearch.service;

import com.deeprunner.docsearch.cache.CacheKeys;
import com.deeprunner.docsearch.domain.dto.SearchResultDto;
import com.deeprunner.docsearch.search.OpenSearchAdapter;
import com.deeprunner.docsearch.search.OpenSearchQueryFactory;
import com.deeprunner.docsearch.web.exception.SearchUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final OpenSearchAdapter openSearchAdapter;
    private final OpenSearchQueryFactory queryFactory;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Timer searchTimer;

    public SearchService(OpenSearchAdapter openSearchAdapter,
                         OpenSearchQueryFactory queryFactory,
                         StringRedisTemplate redisTemplate,
                         ObjectMapper objectMapper,
                         CircuitBreakerRegistry circuitBreakerRegistry,
                         MeterRegistry meterRegistry) {
        this.openSearchAdapter = openSearchAdapter;
        this.queryFactory = queryFactory;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("opensearch");
        this.cacheHitCounter = meterRegistry.counter("dr_search_cache_hits_total");
        this.cacheMissCounter = meterRegistry.counter("dr_search_cache_misses_total");
        this.searchTimer = meterRegistry.timer("dr_search_duration_seconds");
    }

    public SearchResultDto search(String tenantId,
                                  String query,
                                  int from,
                                  int size,
                                  List<String> tags,
                                  boolean highlight,
                                  boolean fuzzy) {
        long startTime = System.currentTimeMillis();

        // 1. Generation counter for O(1) tenant search cache invalidation
        long gen = getTenantSearchGen(tenantId);

        // 2. Canonical query key with SHA-256
        String tagsJoined = tags != null ? String.join(",", tags) : "";
        String queryHash = CacheKeys.canonicalQueryHash(query, tagsJoined, fuzzy, from, size);
        String cacheKey = CacheKeys.searchKey(tenantId, gen, queryHash);

        // 3. Redis cache lookup
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                cacheHitCounter.increment();
                SearchResultDto cachedResult = objectMapper.readValue(cachedJson, SearchResultDto.class);
                long latency = System.currentTimeMillis() - startTime;
                return new SearchResultDto(
                    cachedResult.query(),
                    cachedResult.tenantId(),
                    cachedResult.hits(),
                    cachedResult.page(),
                    latency,
                    true, // cached = true
                    cachedResult.facets()
                );
            }
        } catch (Exception e) {
            log.debug("Search cache read skipped for tenant '{}': {}", tenantId, e.getMessage());
        }

        cacheMissCounter.increment();

        // 4. Construct query strictly with tenant filter
        ObjectNode queryDsl = queryFactory.buildSearchQuery(
            tenantId, query, from, size, tags, fuzzy, highlight
        );

        // 5. Execute OpenSearch search with Circuit Breaker
        SearchResultDto searchResult;
        try {
            searchResult = circuitBreaker.executeCallable(() ->
                openSearchAdapter.executeSearch(tenantId, query, queryDsl, from, size)
            );
        } catch (Exception e) {
            log.error("OpenSearch query failed for tenant '{}': {}", tenantId, e.getMessage());
            throw new SearchUnavailableException("Search service temporarily unavailable", e, 10);
        }

        searchTimer.record(Duration.ofMillis(searchResult.tookMs()));

        // 6. Cache result with TTL jitter (60s ± 20% = 48-72s)
        try {
            int jitteredSeconds = ThreadLocalRandom.current().nextInt(48, 73);
            redisTemplate.opsForValue().set(
                cacheKey,
                objectMapper.writeValueAsString(searchResult),
                Duration.ofSeconds(jitteredSeconds)
            );
        } catch (Exception e) {
            log.debug("Search cache write failed for tenant '{}': {}", tenantId, e.getMessage());
        }

        return searchResult;
    }

    private long getTenantSearchGen(String tenantId) {
        try {
            String genStr = redisTemplate.opsForValue().get(CacheKeys.searchGenKey(tenantId));
            if (genStr != null) {
                return Long.parseLong(genStr);
            }
        } catch (Exception e) {
            log.debug("Could not read search gen for tenant '{}': {}", tenantId, e.getMessage());
        }
        return 1L;
    }
}
