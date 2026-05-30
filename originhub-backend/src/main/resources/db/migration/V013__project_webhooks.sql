CREATE TABLE project_webhooks (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    url        VARCHAR(500) NOT NULL,
    secret     VARCHAR(255),
    enabled    BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_webhook_url UNIQUE (project_id, url)
);

CREATE TABLE project_webhook_events (
    webhook_id UUID        NOT NULL REFERENCES project_webhooks (id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    PRIMARY KEY (webhook_id, event_type)
);

CREATE INDEX idx_project_webhooks_project_id ON project_webhooks (project_id);
