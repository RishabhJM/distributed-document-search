-- V2__init_documents.sql
CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id),
    external_id VARCHAR(256),
    title VARCHAR(512) NOT NULL,
    content TEXT NOT NULL,
    author VARCHAR(256),
    tags TEXT[],
    content_type VARCHAR(64) NOT NULL DEFAULT 'text/plain',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

-- Index for tenant-scoped chronological queries
CREATE INDEX IF NOT EXISTS idx_documents_tenant_created 
    ON documents (tenant_id, created_at DESC) 
    WHERE deleted_at IS NULL;

-- Index for tenant-scoped external identifier lookups
CREATE INDEX IF NOT EXISTS idx_documents_tenant_external_id 
    ON documents (tenant_id, external_id) 
    WHERE deleted_at IS NULL;

-- Index for soft delete checks
CREATE INDEX IF NOT EXISTS idx_documents_tenant_deleted 
    ON documents (tenant_id, deleted_at);
