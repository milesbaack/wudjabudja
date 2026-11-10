-- =============================================================================
-- WudjaBudja Seed Data (Sprint 1)
-- Run after schema.sql:  psql -d wudjabudja -f Code/database/seed.sql
-- Safe to run more than once.
-- =============================================================================

-- One demo user (id 1). The JavaFX app always acts as user 1 until real login lands in Sprint 3.
-- No trips here: trips are created from the app's Trip Setup screen.
INSERT INTO users (id, email, password_hash, full_name)
VALUES (1, 'demo@wudjabudja.local', 'not-a-real-hash', 'Demo Traveler')
ON CONFLICT DO NOTHING;

-- We picked id 1 by hand, so the BIGSERIAL counter doesn't know about it.
-- Move it to the highest id so the next real signup gets 2, not a duplicate 1.
SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
