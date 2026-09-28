package com.deeprunner.docsearch.repository;

import com.deeprunner.docsearch.domain.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    @Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY id ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEventEntity> findPendingForProcessingNative(@Param("limit") int limit);

    List<OutboxEventEntity> findTop200ByStatusOrderByIdAsc(String status);

    long countByStatus(String status);
}
