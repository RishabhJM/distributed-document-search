package com.deeprunner.docsearch.repository;

import com.deeprunner.docsearch.domain.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, String> {

    Optional<TenantEntity> findByIdAndStatus(String id, String status);
}
