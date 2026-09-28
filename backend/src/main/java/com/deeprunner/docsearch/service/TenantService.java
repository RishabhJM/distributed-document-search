package com.deeprunner.docsearch.service;

import com.deeprunner.docsearch.domain.dto.TenantDto;
import java.util.Optional;

@FunctionalInterface
public interface TenantService {
    Optional<TenantDto> findTenant(String tenantId);
}
