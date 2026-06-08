CREATE TABLE workflow_artifact (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id       UUID         NOT NULL REFERENCES workflow_run (id) ON DELETE CASCADE,
    job_id       UUID REFERENCES workflow_job (id) ON DELETE SET NULL,
    name         VARCHAR(256) NOT NULL,
    file_path    TEXT         NOT NULL,
    size_bytes   BIGINT,
    content_type VARCHAR(128),
    expires_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE workflow_secret (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_id         UUID,
    name            VARCHAR(128) NOT NULL,
    encrypted_value TEXT         NOT NULL,
    iv              VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (repo_id, name)
);

CREATE TABLE workflow_cache (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_id      UUID         NOT NULL,
    cache_key    VARCHAR(512) NOT NULL,
    restore_keys TEXT[],
    file_path    TEXT         NOT NULL,
    size_bytes   BIGINT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    accessed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (repo_id, cache_key)
);

CREATE INDEX idx_workflow_artifact_run  ON workflow_artifact (run_id);
CREATE INDEX idx_workflow_secret_repo   ON workflow_secret (repo_id) WHERE repo_id IS NOT NULL;
CREATE INDEX idx_workflow_cache_repo    ON workflow_cache (repo_id, cache_key);
CREATE INDEX idx_workflow_cache_access  ON workflow_cache (accessed_at);
