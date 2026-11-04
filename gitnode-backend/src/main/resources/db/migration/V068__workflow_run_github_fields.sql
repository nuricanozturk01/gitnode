ALTER TABLE workflow_run ADD COLUMN head_commit_message TEXT;
ALTER TABLE workflow_run ADD COLUMN run_attempt INT NOT NULL DEFAULT 1;

CREATE INDEX idx_workflow_run_repo_created ON workflow_run (repo_id, created_at DESC);
