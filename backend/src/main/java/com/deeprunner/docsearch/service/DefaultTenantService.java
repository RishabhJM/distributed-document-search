package com.deeprunner.docsearch.service;

import com.deeprunner.docsearch.domain.dto.TenantDto;
import com.deeprunner.docsearch.repository.TenantRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Primary
public class DefaultTenantService implements TenantService {

    private final TenantRepository tenantRepository;

    public DefaultTenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Optional<TenantDto> findTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        return tenantRepository.findById(tenantId.trim().toLowerCase())
            .map(t -> new TenantDto(
                t.getId(),
                t.getName(),
                t.getTier(),
                t.getRateLimitPerSecond(),
                t.isActive()
            ));
    }
}
