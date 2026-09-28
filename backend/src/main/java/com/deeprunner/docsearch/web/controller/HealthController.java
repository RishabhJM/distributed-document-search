package com.deeprunner.docsearch.web.controller;

import com.deeprunner.docsearch.health.DependencyHealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/health", "/api/v1/health"})
public class HealthController {

    private final DependencyHealthIndicator healthIndicator;

    public HealthController(DependencyHealthIndicator healthIndicator) {
        this.healthIndicator = healthIndicator;
    }

    @GetMapping
    public ResponseEntity<DependencyHealthIndicator.HealthStatus> checkHealth() {
        DependencyHealthIndicator.HealthStatus status = healthIndicator.checkAll();
        if ("DOWN".equals(status.status())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
        }
        return ResponseEntity.ok(status);
    }
}
