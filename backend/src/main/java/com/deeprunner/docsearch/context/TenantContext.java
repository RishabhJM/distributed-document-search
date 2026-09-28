package com.deeprunner.docsearch.context;

import java.util.Optional;

/**
 * ThreadLocal storage holding the active tenant identity for the current request.
 * Populated by TenantResolutionFilter and cleared in a finally block.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be null or blank");
        }
        CURRENT_TENANT.set(tenantId.trim().toLowerCase());
    }

    public static Optional<String> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static String requireTenantId() {
        return getTenantId().orElseThrow(() -> 
            new IllegalStateException("No tenant present in current execution context"));
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
