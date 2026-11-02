CREATE TABLE branch_protection_rules (
  id UUID PRIMARY KEY,
  repo_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  branch_name VARCHAR(255) NOT NULL,
  required_approvals INTEGER NOT NULL DEFAULT 0 CHECK (required_approvals BETWEEN 0 AND 20),
  required_checks TEXT NOT NULL DEFAULT '',
  UNIQUE (repo_id, branch_name)
);
