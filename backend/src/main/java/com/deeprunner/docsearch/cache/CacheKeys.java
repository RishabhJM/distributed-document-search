package com.deeprunner.docsearch.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String documentKey(String tenantId, Object documentId) {
        validateTenant(tenantId);
        return "doc:v1:" + tenantId + ":" + documentId;
    }

    public static String searchGenKey(String tenantId) {
        validateTenant(tenantId);
        return "searchgen:v1:" + tenantId;
    }

    public static String searchKey(String tenantId, long generation, String canonicalQueryHash) {
        validateTenant(tenantId);
        return "search:v1:" + tenantId + ":" + generation + ":" + canonicalQueryHash;
    }

    public static String rateLimitKey(String tenantId) {
        validateTenant(tenantId);
        return "ratelimit:v1:" + tenantId;
    }

    public static String tenantMetaKey(String tenantId) {
        validateTenant(tenantId);
        return "tenant:v1:" + tenantId;
    }

    public static String canonicalQueryHash(String query, String tags, boolean fuzzy, int from, int size) {
        String normalizedQuery = query == null ? "" : query.trim().replaceAll("\\s+", " ").toLowerCase();
        String normalizedTags = tags == null ? "" : tags.trim().toLowerCase();
        String raw = normalizedQuery + "|tags=" + normalizedTags + "|fuzzy=" + fuzzy + "|from=" + from + "|size=" + size;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void validateTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID cannot be null or blank when building cache keys");
        }
    }
}
