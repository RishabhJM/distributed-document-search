package com.deeprunner.docsearch.web.filter;

import com.deeprunner.docsearch.context.AppRequestContextFilter;
import com.deeprunner.docsearch.context.TenantContext;
import com.deeprunner.docsearch.domain.dto.ProblemDetailsDto;
import com.deeprunner.docsearch.domain.dto.TenantDto;
import com.deeprunner.docsearch.service.RateLimiter;
import com.deeprunner.docsearch.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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

/**
 * Filter 20 in the chain: Per-tenant rate limiting via token bucket.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final TenantService tenantService;
    private final ObjectMapper objectMapper;
    private final Counter rateLimitRejectionsCounter;

    public RateLimitFilter(RateLimiter rateLimiter,
                           TenantService tenantService,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.tenantService = tenantService;
        this.objectMapper = objectMapper;
        this.rateLimitRejectionsCounter = meterRegistry.counter("dr_ratelimit_rejections_total");
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
        String tenantId = TenantContext.getTenantId().orElse(null);
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int quota = tenantService.findTenant(tenantId)
            .map(TenantDto::rateLimitPerSecond)
            .orElse(50);

        RateLimiter.RateLimitResult result = rateLimiter.tryConsume(tenantId, quota);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));

        if (!result.allowed()) {
            rateLimitRejectionsCounter.increment();
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(Math.max(1, result.resetSeconds())));
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");

            String requestId = MDC.get(AppRequestContextFilter.MDC_REQUEST_ID_KEY);
            ProblemDetailsDto problem = ProblemDetailsDto.rateLimit(
                (int) Math.max(1, result.resetSeconds()),
                "Tenant '" + tenantId + "' exceeded " + quota + " requests/second.",
                requestId
            );
            response.getWriter().write(objectMapper.writeValueAsString(problem));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
