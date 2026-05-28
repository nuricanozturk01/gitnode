CREATE TABLE IF NOT EXISTS issues
(
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  repo_id     UUID         NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
  number      INT          NOT NULL,
  title       VARCHAR(255) NOT NULL,
  description TEXT,
  status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
    CHECK (status IN ('OPEN', 'CLOSED')),
  author_id   UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  assignee_id UUID         REFERENCES tenant (id) ON DELETE SET NULL,
  created_at  TIMESTAMP WITHOUT TIME ZONE,
  updated_at  TIMESTAMP WITHOUT TIME ZONE,
  closed_at   TIMESTAMP WITHOUT TIME ZONE,

  UNIQUE (repo_id, number)
);

CREATE INDEX IF NOT EXISTS idx_issues_repo_id ON issues (repo_id);
CREATE INDEX IF NOT EXISTS idx_issues_repo_status ON issues (repo_id, status);

CREATE TABLE IF NOT EXISTS issue_comments
(
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  issue_id   UUID NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
  author_id  UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  body       TEXT NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE,
  updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_issue_comments_issue_id ON issue_comments (issue_id);

ALTER TABLE tasks
  ADD COLUMN IF NOT EXISTS linked_issue_id UUID REFERENCES issues (id) ON DELETE SET NULL;
