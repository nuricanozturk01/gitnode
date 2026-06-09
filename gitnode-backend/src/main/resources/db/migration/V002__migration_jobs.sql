CREATE TABLE IF NOT EXISTS migration_jobs
(
  id           UUID PRIMARY KEY NOT NULL,
  service      VARCHAR(50)  NOT NULL
    CHECK (service IN ('GITHUB')),
  status       VARCHAR(20)  NOT NULL
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
  repo_url     VARCHAR(500),
  repo_owner   VARCHAR(255),
  repo_name    VARCHAR(100),
  error_msg    TEXT,
  requester_id UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  created_at   TIMESTAMP WITHOUT TIME ZONE,
  completed_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE IF NOT EXISTS migration_jobs_migration_items
(
  migration_job_id UUID        NOT NULL REFERENCES migration_jobs (id) ON DELETE CASCADE,
  migration_items  VARCHAR(50) NOT NULL
    CHECK (migration_items IN ('REPOSITORIES', 'PULL_REQUESTS'))
);
