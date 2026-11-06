-- Foreign keys without an index on the referencing column make every delete of the parent row
-- (an account, a repository, a label, ...) scan the child table, and cascades multiply that.
-- Case-insensitive user name lookups (upper(username) = upper(?)) also had no index.

CREATE INDEX IF NOT EXISTS idx_tenant_username_upper ON tenant (upper(username));

CREATE INDEX IF NOT EXISTS idx_notification_actor_type ON notification (actor_id, type);

CREATE INDEX IF NOT EXISTS idx_ai_codebase_analyses_triggered_by ON ai_codebase_analyses (triggered_by) WHERE triggered_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_discussion_comments_author ON discussion_comments (author_id);
CREATE INDEX IF NOT EXISTS idx_discussion_comments_parent ON discussion_comments (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_discussion_votes_tenant ON discussion_votes (tenant_id);
CREATE INDEX IF NOT EXISTS idx_discussions_author ON discussions (author_id);
CREATE INDEX IF NOT EXISTS idx_discussions_answer_comment ON discussions (answer_comment_id) WHERE answer_comment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_issue_label_assignments_label ON issue_label_assignments (label_id);
CREATE INDEX IF NOT EXISTS idx_migration_jobs_requester ON migration_jobs (requester_id);
CREATE INDEX IF NOT EXISTS idx_migration_items_job ON migration_jobs_migration_items (migration_job_id);
CREATE INDEX IF NOT EXISTS idx_pr_assignees_tenant ON pr_assignees (tenant_id);
CREATE INDEX IF NOT EXISTS idx_pr_label_assignments_label ON pr_label_assignments (label_id);
CREATE INDEX IF NOT EXISTS idx_pr_reviewers_tenant ON pr_reviewers (tenant_id);
CREATE INDEX IF NOT EXISTS idx_pr_reviews_author ON pr_reviews (author_id);
CREATE INDEX IF NOT EXISTS idx_project_repos_repo ON project_repos (repo_id);
CREATE INDEX IF NOT EXISTS idx_pull_requests_auto_merge_by ON pull_requests (auto_merge_by_id) WHERE auto_merge_by_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_release_assets_uploader ON release_assets (uploader_id) WHERE uploader_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_repo_collaborators_invited_by ON repo_collaborators (invited_by_id);
CREATE INDEX IF NOT EXISTS idx_repositories_forked_from ON repositories (forked_from_id) WHERE forked_from_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_runner_group ON runner (group_id) WHERE group_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_runner_registration_token_runner ON runner_registration_token (runner_id) WHERE runner_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_snippet_repos_repo ON snippet_repos (repo_id);
CREATE INDEX IF NOT EXISTS idx_workflow_artifact_job ON workflow_artifact (job_id) WHERE job_id IS NOT NULL;
