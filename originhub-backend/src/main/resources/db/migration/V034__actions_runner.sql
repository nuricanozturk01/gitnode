CREATE TABLE runner_registration_token (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_id     UUID,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    created_by  UUID         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE runner (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_id         UUID,
    name            VARCHAR(128) NOT NULL,
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    labels          TEXT[]       NOT NULL DEFAULT '{self-hosted}',
    os              VARCHAR(32),
    arch            VARCHAR(16),
    status          VARCHAR(16)  NOT NULL DEFAULT 'offline',
    executor_type   VARCHAR(16)  NOT NULL DEFAULT 'shell',
    version         VARCHAR(32),
    last_heartbeat  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID
);

CREATE INDEX idx_runner_repo_status   ON runner (repo_id, status);
CREATE INDEX idx_runner_labels        ON runner USING GIN (labels);
CREATE INDEX idx_runner_last_heartbeat ON runner (last_heartbeat) WHERE status != 'offline';
