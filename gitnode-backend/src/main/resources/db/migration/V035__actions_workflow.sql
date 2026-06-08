CREATE TABLE workflow_definition (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_id      UUID         NOT NULL,
    name         VARCHAR(256) NOT NULL,
    file_path    VARCHAR(512) NOT NULL,
    content      TEXT         NOT NULL,
    content_hash VARCHAR(64),
    is_enabled   BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (repo_id, file_path)
);

CREATE TABLE workflow_run (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_id         UUID         NOT NULL,
    workflow_def_id UUID REFERENCES workflow_definition (id) ON DELETE SET NULL,
    workflow_name   VARCHAR(256) NOT NULL,
    run_number      INTEGER      NOT NULL,
    trigger_event   VARCHAR(32)  NOT NULL,
    trigger_ref     VARCHAR(256),
    trigger_sha     VARCHAR(64),
    trigger_actor   UUID,
    status          VARCHAR(16)  NOT NULL DEFAULT 'queued',
    conclusion      VARCHAR(16),
    inputs          JSONB,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (repo_id, run_number)
);

CREATE TABLE workflow_job (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id        UUID         NOT NULL REFERENCES workflow_run (id) ON DELETE CASCADE,
    name          VARCHAR(256) NOT NULL,
    runner_id     UUID REFERENCES runner (id) ON DELETE SET NULL,
    runner_labels TEXT[]       NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'queued',
    conclusion    VARCHAR(16),
    matrix_values JSONB,
    needs         TEXT[],
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE workflow_step (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id       UUID         NOT NULL REFERENCES workflow_job (id) ON DELETE CASCADE,
    step_number  INTEGER      NOT NULL,
    name         VARCHAR(256),
    uses         VARCHAR(256),
    status       VARCHAR(16)  NOT NULL DEFAULT 'pending',
    conclusion   VARCHAR(16),
    outputs      JSONB,
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE TABLE workflow_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    step_id     UUID        NOT NULL REFERENCES workflow_step (id) ON DELETE CASCADE,
    line_number INTEGER     NOT NULL,
    content     TEXT        NOT NULL,
    level       VARCHAR(8)  NOT NULL DEFAULT 'info',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflow_def_repo        ON workflow_definition (repo_id);
CREATE INDEX idx_workflow_run_repo        ON workflow_run (repo_id, created_at DESC);
CREATE INDEX idx_workflow_run_status      ON workflow_run (status) WHERE status IN ('queued', 'in_progress');
CREATE INDEX idx_workflow_job_run         ON workflow_job (run_id, status);
CREATE INDEX idx_workflow_job_queued      ON workflow_job (status, runner_labels) WHERE status = 'queued';
CREATE INDEX idx_workflow_step_job        ON workflow_step (job_id, step_number);
CREATE INDEX idx_workflow_log_step        ON workflow_log (step_id, line_number);
