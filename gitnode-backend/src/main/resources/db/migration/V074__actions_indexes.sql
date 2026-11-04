-- Indexes for the Actions queries, found by comparing the real query predicates with what exists.

-- Statuses are stored upper-case (EnumType.STRING). The partial indexes below were declared with
-- lower-case literals ('queued', 'in_progress', 'offline') so no query could ever use them.
DROP INDEX IF EXISTS idx_workflow_job_queued;
DROP INDEX IF EXISTS idx_workflow_run_status;
DROP INDEX IF EXISTS idx_runner_last_heartbeat;

-- Dispatcher: oldest QUEUED jobs first (FOR UPDATE SKIP LOCKED) and the housekeeping sweep.
CREATE INDEX IF NOT EXISTS idx_workflow_job_queued_created
  ON workflow_job (created_at) WHERE status = 'QUEUED';

-- Concurrency groups and "how many runs are active".
CREATE INDEX IF NOT EXISTS idx_workflow_run_active
  ON workflow_run (repo_id, concurrency_group) WHERE status IN ('QUEUED', 'IN_PROGRESS');

-- Runners that stopped sending heartbeats.
CREATE INDEX IF NOT EXISTS idx_runner_status_heartbeat ON runner (status, last_heartbeat);

-- Run list filters (event, status, actor, branch), each newest first like the list itself.
CREATE INDEX IF NOT EXISTS idx_workflow_run_repo_event
  ON workflow_run (repo_id, trigger_event, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_run_repo_status
  ON workflow_run (repo_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_run_repo_actor
  ON workflow_run (repo_id, trigger_actor, created_at DESC) WHERE trigger_actor IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_workflow_run_repo_ref
  ON workflow_run (repo_id, trigger_ref, created_at DESC) WHERE trigger_ref IS NOT NULL;

-- Required checks of a pull request: the run for a commit and workflow name.
CREATE INDEX IF NOT EXISTS idx_workflow_run_sha_name
  ON workflow_run (repo_id, trigger_sha, workflow_name) WHERE trigger_sha IS NOT NULL;

-- Deleting or replacing a workflow definition checks the runs that point at it (ON DELETE SET NULL).
CREATE INDEX IF NOT EXISTS idx_workflow_run_def
  ON workflow_run (workflow_def_id) WHERE workflow_def_id IS NOT NULL;

-- Artifact download looks an artifact up by run and name.
CREATE INDEX IF NOT EXISTS idx_workflow_artifact_run_name ON workflow_artifact (run_id, name);

-- Cache restore-keys match by key prefix (LIKE 'prefix%'), which a plain btree cannot serve.
CREATE INDEX IF NOT EXISTS idx_workflow_cache_key_prefix
  ON workflow_cache (repo_id, cache_key text_pattern_ops);

-- Duplicates of an index that already exists:
--   V068's index equals idx_workflow_run_repo (repo_id, created_at DESC);
--   idx_workflow_cache_repo equals the unique constraint on (repo_id, cache_key).
DROP INDEX IF EXISTS idx_workflow_run_repo_created;
DROP INDEX IF EXISTS idx_workflow_cache_repo;
