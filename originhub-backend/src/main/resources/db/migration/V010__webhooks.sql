CREATE TABLE webhooks (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_id     UUID         NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL,
    secret      VARCHAR(255),
    enabled     BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_webhook_repo_url UNIQUE (repo_id, url)
);

CREATE TABLE webhook_events (
    webhook_id UUID        NOT NULL REFERENCES webhooks (id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    PRIMARY KEY (webhook_id, event_type)
);

CREATE INDEX idx_webhooks_repo_id ON webhooks (repo_id);
