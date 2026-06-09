CREATE TABLE user_webhooks (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL,
    secret      VARCHAR(255),
    enabled     BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_webhook_url UNIQUE (user_id, url)
);

CREATE TABLE user_webhook_events (
    webhook_id UUID        NOT NULL REFERENCES user_webhooks (id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    PRIMARY KEY (webhook_id, event_type)
);

CREATE INDEX idx_user_webhooks_user_id ON user_webhooks (user_id);
