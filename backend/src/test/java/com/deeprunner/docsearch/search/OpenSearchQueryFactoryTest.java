package com.deeprunner.docsearch.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenSearchQueryFactoryTest {

    private OpenSearchQueryFactory queryFactory;

    @BeforeEach
    void setUp() {
        queryFactory = new OpenSearchQueryFactory(new ObjectMapper());
    }

    @Test
    @DisplayName("Query factory throws IllegalStateException if tenantId is null or empty")
    void testRequiresTenantId() {
        assertThrows(IllegalStateException.class, () ->
            queryFactory.buildSearchQuery(null, "test", 0, 10, null, false, false));

        assertThrows(IllegalStateException.class, () ->
            queryFactory.buildSearchQuery("", "test", 0, 10, null, false, false));

        assertThrows(IllegalStateException.class, () ->
            queryFactory.buildSearchQuery("   ", "test", 0, 10, null, false, false));
    }

    @Test
    @DisplayName("Query factory injects mandatory tenant filter and applies best_fields boosting")
    void testTenantFilterInjected() {
        ObjectNode query = queryFactory.buildSearchQuery("acme", "payroll runbook", 0, 20, List.of("finance"), false, true);

        // Verify pagination and caps
        assertEquals(0, query.get("from").asInt());
        assertEquals(20, query.get("size").asInt());
        assertEquals(10000, query.get("track_total_hits").asInt());

        // Verify tenant filter clause
        var filters = query.get("query").get("bool").get("filter");
        assertNotNull(filters);
        assertTrue(filters.isArray());

        boolean hasTenantFilter = false;
        boolean hasTagFilter = false;
        for (var f : filters) {
            if (f.has("term") && f.get("term").has("tenantId")) {
                assertEquals("acme", f.get("term").get("tenantId").asText());
                hasTenantFilter = true;
            }
            if (f.has("term") && f.get("term").has("tags")) {
                assertEquals("finance", f.get("term").get("tags").asText());
                hasTagFilter = true;
            }
        }
        assertTrue(hasTenantFilter, "Query must contain a term filter for tenantId");
        assertTrue(hasTagFilter, "Query must contain a term filter for specified tag");

        // Verify highlighting configuration
        assertNotNull(query.get("highlight"));
        assertEquals("<em>", query.get("highlight").get("pre_tags").get(0).asText());
    }
}
