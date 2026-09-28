package com.deeprunner.docsearch.repository;

import com.deeprunner.docsearch.domain.entity.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    /**
     * Tenant-scoped lookup. Un-tenanted findById is prohibited by ArchUnit rules.
     */
    Optional<DocumentEntity> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, UUID id);

    Optional<DocumentEntity> findByTenantIdAndExternalIdAndDeletedAtIsNull(String tenantId, String externalId);

    Page<DocumentEntity> findAllByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);

    long countByTenantIdAndDeletedAtIsNull(String tenantId);

    List<DocumentEntity> findAllByTenantIdAndIdInAndDeletedAtIsNull(String tenantId, List<UUID> ids);
}
