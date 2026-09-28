package com.deeprunner.docsearch.web.filter;

import com.deeprunner.docsearch.context.RequestContextFilter;
import com.deeprunner.docsearch.context.TenantContext;
import com.deeprunner.docsearch.domain.dto.ProblemDetailsDto;
import com.deeprunner.docsearch.domain.dto.TenantDto;
import com.deeprunner.docsearch.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Filter 10 in the security chain: Tenant Resolution.
 * FAILS CLOSED: Rejects missing, malformed, mismatched, or unknown tenants before any controller runs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantResolutionFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";
    public static final String TENANT_PARAM = "tenant";
    public static final String MDC_TENANT_KEY = "tenantId";
    private static final Pattern TENANT_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final TenantService tenantService;
    private final ObjectMapper objectMapper;

    public TenantResolutionFilter(TenantService tenantService, ObjectMapper objectMapper) {
        this.tenantService = tenantService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = (String) request.getAttribute(RequestContextFilter.MDC_REQUEST_ID_KEY);
        if (requestId == null) {
            requestId = MDC.get(RequestContextFilter.MDC_REQUEST_ID_KEY);
        }

        String tenantHeader = request.getHeader(TENANT_HEADER);

        // 1. Missing tenant check
        if (tenantHeader == null || tenantHeader.isBlank()) {
            writeProblemResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                "missing-tenant", "Missing tenant identifier",
                "X-Tenant-ID header is required", "MISSING_TENANT", requestId);
            return;
        }

        String tenantId = tenantHeader.trim().toLowerCase();

        // 2. Malformed tenant check
        if (!TENANT_PATTERN.matcher(tenantId).matches()) {
            writeProblemResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                "invalid-tenant", "Invalid tenant identifier",
                "X-Tenant-ID must match pattern ^[a-zA-Z0-9_-]{1,64}$", "MALFORMED_TENANT", requestId);
            return;
        }

        // 3. Mismatch check: query parameter cannot conflict with authoritative header
        String tenantParam = request.getParameter(TENANT_PARAM);
        if (tenantParam != null && !tenantParam.isBlank() && !tenantParam.trim().equalsIgnoreCase(tenantId)) {
            writeProblemResponse(response, HttpServletResponse.SC_FORBIDDEN,
                "tenant-mismatch", "Tenant mismatch",
                "Query parameter 'tenant' conflicts with authoritative X-Tenant-ID header", "TENANT_MISMATCH", requestId);
            return;
        }

        // 4. Validate tenant existence and active status
        Optional<TenantDto> tenantOpt = tenantService.findTenant(tenantId);
        if (tenantOpt.isEmpty() || !tenantOpt.get().active()) {
            // Identical 403 returned for unknown and inactive to prevent enumeration attacks
            writeProblemResponse(response, HttpServletResponse.SC_FORBIDDEN,
                "tenant-access-denied", "Tenant access denied",
                "Tenant is invalid or not active", "TENANT_ACCESS_DENIED", requestId);
            return;
        }

        TenantContext.setTenantId(tenantId);
        MDC.put(MDC_TENANT_KEY, tenantId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_TENANT_KEY);
        }
    }

    private void writeProblemResponse(HttpServletResponse response,
                                      int status,
                                      String slug,
                                      String title,
                                      String detail,
                                      String code,
                                      String requestId) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        ProblemDetailsDto problem = ProblemDetailsDto.of(slug, title, status, detail, code, requestId);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
