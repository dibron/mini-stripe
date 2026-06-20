-- =============================================================================
-- PostgreSQL initialization — Stripe Learning Platform
-- Creates one schema per service for isolation.
-- In production: separate database clusters per service.
-- =============================================================================

-- Create per-service schemas
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS wallet;
CREATE SCHEMA IF NOT EXISTS ledger;
CREATE SCHEMA IF NOT EXISTS saga;

-- Create per-service users (principle of least privilege)
CREATE USER payment_svc WITH PASSWORD 'payment_dev';
CREATE USER wallet_svc  WITH PASSWORD 'wallet_dev';
CREATE USER ledger_svc  WITH PASSWORD 'ledger_dev';
CREATE USER saga_svc    WITH PASSWORD 'saga_dev';

-- Grant schema-level access only
GRANT USAGE ON SCHEMA payment TO payment_svc;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA payment TO payment_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA payment GRANT ALL ON TABLES TO payment_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA payment GRANT ALL ON SEQUENCES TO payment_svc;

GRANT USAGE ON SCHEMA wallet TO wallet_svc;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA wallet TO wallet_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA wallet GRANT ALL ON TABLES TO wallet_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA wallet GRANT ALL ON SEQUENCES TO wallet_svc;

GRANT USAGE ON SCHEMA ledger TO ledger_svc;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA ledger TO ledger_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA ledger GRANT ALL ON TABLES TO ledger_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA ledger GRANT ALL ON SEQUENCES TO ledger_svc;

GRANT USAGE ON SCHEMA saga TO saga_svc;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA saga TO saga_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA saga GRANT ALL ON TABLES TO saga_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA saga GRANT ALL ON SEQUENCES TO saga_svc;
