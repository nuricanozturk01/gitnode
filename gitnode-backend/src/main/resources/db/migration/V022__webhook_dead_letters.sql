CREATE TABLE webhook_dead_letters
(
    id            UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    webhook_id    UUID         NOT NULL,
    url           VARCHAR(500) NOT NULL,
    event_type    VARCHAR(100),
    payload       TEXT,
    error_message TEXT,
    attempt_count INT          NOT NULL    DEFAULT 3,
    failed_at     TIMESTAMPTZ  NOT NULL    DEFAULT NOW()
);

CREATE INDEX idx_webhook_dead_letters_webhook_id ON webhook_dead_letters (webhook_id);
CREATE INDEX idx_webhook_dead_letters_failed_at ON webhook_dead_letters (failed_at);
