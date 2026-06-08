ALTER TABLE webhook_dead_letters
    ADD COLUMN next_retry_at  TIMESTAMPTZ,
    ADD COLUMN dlq_retry_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_webhook_dead_letters_next_retry ON webhook_dead_letters (next_retry_at)
    WHERE next_retry_at IS NOT NULL;
