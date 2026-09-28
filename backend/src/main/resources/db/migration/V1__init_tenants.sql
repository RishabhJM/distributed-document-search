-- V1__init_tenants.sql
CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    tier VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    rate_limit_per_second INT NOT NULL DEFAULT 50,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default tenants matching design specs
INSERT INTO tenants (id, name, tier, rate_limit_per_second, status) VALUES
    ('acme', 'Acme Corporation', 'ENTERPRISE', 50, 'ACTIVE'),
    ('globex', 'Globex International', 'ENTERPRISE', 100, 'ACTIVE'),
    ('initech', 'Initech Corporation', 'STANDARD', 20, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;
