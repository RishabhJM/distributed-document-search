package com.deeprunner.docsearch.health;

import com.deeprunner.docsearch.search.OpenSearchAdapter;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DependencyHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;
    private final OpenSearchAdapter openSearchAdapter;
    private final RedisConnectionFactory redisConnectionFactory;

    public DependencyHealthIndicator(JdbcTemplate jdbcTemplate,
                                       OpenSearchAdapter openSearchAdapter,
                                       RedisConnectionFactory redisConnectionFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.openSearchAdapter = openSearchAdapter;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    public HealthStatus checkAll() {
        boolean postgresUp = isPostgresUp();
        boolean openSearchUp = openSearchAdapter.isHealthy();
        boolean redisUp = isRedisUp();

        Map<String, DependencyStatus> deps = new LinkedHashMap<>();
        deps.put("postgres", new DependencyStatus(postgresUp ? "UP" : "DOWN", true));
        deps.put("opensearch", new DependencyStatus(openSearchUp ? "UP" : "DOWN", true));
        deps.put("redis", new DependencyStatus(redisUp ? "UP" : "DEGRADED", false));

        String overallStatus;
        if (!postgresUp || !openSearchUp) {
            overallStatus = "DOWN";
        } else if (!redisUp) {
            overallStatus = "DEGRADED"; // Non-fatal dependency degrades gracefully
        } else {
            overallStatus = "UP";
        }

        return new HealthStatus(overallStatus, deps);
    }

    @Override
    public Health health() {
        HealthStatus status = checkAll();
        Health.Builder builder = "DOWN".equals(status.status()) ? Health.down() : Health.up();
        builder.withDetail("status", status.status());
        status.dependencies().forEach((k, v) -> builder.withDetail(k, v.status()));
        return builder.build();
    }

    private boolean isPostgresUp() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isRedisUp() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String ping = connection.ping();
            return "PONG".equalsIgnoreCase(ping);
        } catch (Exception e) {
            return false;
        }
    }

    public record DependencyStatus(String status, boolean critical) {}
    public record HealthStatus(String status, Map<String, DependencyStatus> dependencies) {}
}
