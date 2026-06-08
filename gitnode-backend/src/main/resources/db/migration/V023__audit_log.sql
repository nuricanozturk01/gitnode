CREATE TABLE audit_logs
(
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    actor_username VARCHAR(100),
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(100),
    entity_id      VARCHAR(255),
    details        TEXT,
    ip_address     VARCHAR(45),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- ── Partitions ─────────────────────────────────────────────────────────────────

CREATE TABLE audit_logs_2026_h1 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');

CREATE TABLE audit_logs_2026_h2 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

CREATE TABLE audit_logs_2027_h1 PARTITION OF audit_logs
    FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2027-07-01 00:00:00+00');

CREATE TABLE audit_logs_2027_h2 PARTITION OF audit_logs
    FOR VALUES FROM ('2027-07-01 00:00:00+00') TO ('2028-01-01 00:00:00+00');

CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

CREATE INDEX idx_audit_logs_occurred_at_brin
    ON audit_logs USING BRIN (occurred_at) WITH (pages_per_range = 128);

CREATE INDEX idx_audit_logs_actor
    ON audit_logs (actor_username)
    WHERE actor_username IS NOT NULL;

CREATE INDEX idx_audit_logs_action
    ON audit_logs (action);

CREATE INDEX idx_audit_logs_entity
    ON audit_logs (entity_type, entity_id)
    WHERE entity_type IS NOT NULL;

-- ── Append-only enforcement ─────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION audit_logs_immutable()
    RETURNS TRIGGER LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % not allowed', TG_OP;
END;
$$;

CREATE TRIGGER audit_logs_no_update
    BEFORE UPDATE
    ON audit_logs
    FOR EACH ROW
EXECUTE FUNCTION audit_logs_immutable();

CREATE TRIGGER audit_logs_no_delete
    BEFORE DELETE
    ON audit_logs
    FOR EACH ROW
EXECUTE FUNCTION audit_logs_immutable();
