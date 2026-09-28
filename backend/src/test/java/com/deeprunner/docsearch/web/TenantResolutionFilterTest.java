package com.deeprunner.docsearch.web;

import com.deeprunner.docsearch.context.TenantContext;
import com.deeprunner.docsearch.domain.dto.TenantDto;
import com.deeprunner.docsearch.service.TenantService;
import com.deeprunner.docsearch.web.filter.TenantResolutionFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TenantResolutionFilterTest {

    private Map<String, TenantDto> tenantRegistry;
    private TenantResolutionFilter filter;

    @BeforeEach
    void setUp() {
        tenantRegistry = new HashMap<>();
        TenantService stubService = tenantId -> Optional.ofNullable(tenantRegistry.get(tenantId));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        filter = new TenantResolutionFilter(stubService, mapper);
        TenantContext.clear();
    }

    @Test
    @DisplayName("Missing X-Tenant-ID header returns HTTP 400 with MISSING_TENANT code")
    void testMissingTenantHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/documents/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("MISSING_TENANT"));
    }

    @Test
    @DisplayName("Malformed X-Tenant-ID header returns HTTP 400 with MALFORMED_TENANT code")
    void testMalformedTenantHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/documents/123");
        request.addHeader("X-Tenant-ID", "invalid;drop table;--");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("MALFORMED_TENANT"));
    }

    @Test
    @DisplayName("Conflicting query parameter and header returns HTTP 403 TENANT_MISMATCH")
    void testTenantParamMismatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/search");
        request.addHeader("X-Tenant-ID", "acme");
        request.setParameter("tenant", "globex");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("TENANT_MISMATCH"));
    }

    @Test
    @DisplayName("Unknown or suspended tenant returns HTTP 403 TENANT_ACCESS_DENIED without leaking existence")
    void testUnknownTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/documents/123");
        request.addHeader("X-Tenant-ID", "unknown-corp");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("TENANT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Valid active tenant populates context and cleans up after request")
    void testValidTenant() throws Exception {
        tenantRegistry.put("acme", new TenantDto("acme", "Acme Corp", "ENTERPRISE", 50, true));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/documents/123");
        request.addHeader("X-Tenant-ID", "acme");

        MockHttpServletResponse response = new MockHttpServletResponse();
        final String[] observedTenantInChain = new String[1];

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                observedTenantInChain[0] = TenantContext.getTenantId().orElse(null);
            }
        };

        filter.doFilter(request, response, chain);

        assertEquals("acme", observedTenantInChain[0], "TenantContext must be populated inside chain");
        assertTrue(TenantContext.getTenantId().isEmpty(), "TenantContext must be cleared after request");
    }
}
