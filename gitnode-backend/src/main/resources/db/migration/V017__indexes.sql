-- Missing foreign key indexes for frequently queried columns.
-- Covering the most impactful FK columns not yet indexed.

-- sso_account
CREATE INDEX IF NOT EXISTS idx_sso_account_tenant_id ON sso_account (tenant_id);

-- ssh_keys
CREATE INDEX IF NOT EXISTS idx_ssh_keys_tenant_id ON ssh_keys (tenant_id);

-- repositories
CREATE INDEX IF NOT EXISTS idx_repositories_owner_id ON repositories (owner_id);

-- pull_requests: repo+status composite covers the most common filtered list query
CREATE INDEX IF NOT EXISTS idx_pull_requests_repo_status ON pull_requests (repo_id, status);
CREATE INDEX IF NOT EXISTS idx_pull_requests_author_id ON pull_requests (author_id);

-- pull_request_comments
CREATE INDEX IF NOT EXISTS idx_pr_comments_pr_id ON pull_request_comments (pr_id);
CREATE INDEX IF NOT EXISTS idx_pr_comments_author_id ON pull_request_comments (author_id);

-- issues: author and assignee lookups
CREATE INDEX IF NOT EXISTS idx_issues_author_id ON issues (author_id);
CREATE INDEX IF NOT EXISTS idx_issues_assignee_id ON issues (assignee_id);

-- issue_comments: author lookup
CREATE INDEX IF NOT EXISTS idx_issue_comments_author_id ON issue_comments (author_id);

-- tasks: assignee and linked-entity columns
CREATE INDEX IF NOT EXISTS idx_tasks_assignee_id ON tasks (assignee_id);
CREATE INDEX IF NOT EXISTS idx_tasks_linked_issue_id ON tasks (linked_issue_id);
CREATE INDEX IF NOT EXISTS idx_tasks_linked_pr_id ON tasks (linked_pr_id);

-- subtasks: assignee lookup (column added in later migration)
CREATE INDEX IF NOT EXISTS idx_subtasks_linked_pr_id ON subtasks (linked_pr_id);

-- snippet_comments: author lookup
CREATE INDEX IF NOT EXISTS idx_snippet_comments_author_id ON snippet_comments (author_id);
