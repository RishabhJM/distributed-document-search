-- V3__init_outbox.sql
CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    document_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Partial index for high-throughput skip-locked draining of pending events
CREATE INDEX IF NOT EXISTS idx_outbox_pending_id 
    ON outbox_events (status, id) 
    WHERE status = 'PENDING';

-- Aggressive autovacuum for high update/delete churn outbox table
ALTER TABLE outbox_events SET (autovacuum_vacuum_scale_factor = 0.01);
