ALTER TABLE runner RENAME COLUMN repo_id TO tenant_id;
ALTER TABLE runner_registration_token RENAME COLUMN repo_id TO tenant_id;

DROP INDEX IF EXISTS idx_runner_repo_status;
CREATE INDEX idx_runner_tenant_status ON runner (tenant_id, status);
