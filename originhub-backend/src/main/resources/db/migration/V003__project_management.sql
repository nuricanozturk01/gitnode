CREATE TABLE IF NOT EXISTS projects
(
  id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
  owner_id    UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  name        VARCHAR(120) NOT NULL,
  description TEXT,
  code_prefix VARCHAR(10)  NOT NULL,
  task_seq    BIGINT       NOT NULL DEFAULT 0,
  created_at  TIMESTAMP WITHOUT TIME ZONE,
  updated_at  TIMESTAMP WITHOUT TIME ZONE,

  UNIQUE (owner_id, name),
  UNIQUE (owner_id, code_prefix)
);

CREATE INDEX IF NOT EXISTS idx_projects_owner_id ON projects (owner_id);

ALTER TABLE repositories
  ADD COLUMN IF NOT EXISTS project_id UUID
    REFERENCES projects (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_repositories_project_id ON repositories (project_id);

CREATE TABLE IF NOT EXISTS boards
(
  id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
  project_id UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
  name       VARCHAR(120) NOT NULL,
  position   INT          NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITHOUT TIME ZONE,
  updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_boards_project_id ON boards (project_id);

CREATE TABLE IF NOT EXISTS board_columns
(
  id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
  board_id   UUID         NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
  name       VARCHAR(120) NOT NULL,
  color      VARCHAR(20),
  position   INT          NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITHOUT TIME ZONE,
  updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_board_columns_board_id ON board_columns (board_id);

CREATE TABLE IF NOT EXISTS tasks
(
  id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
  project_id      UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
  board_column_id UUID         NOT NULL REFERENCES board_columns (id) ON DELETE CASCADE,
  code            VARCHAR(40)  NOT NULL,
  title           VARCHAR(255) NOT NULL,
  description     TEXT,
  type            VARCHAR(20)  NOT NULL DEFAULT 'TASK'
    CHECK (type IN ('TASK', 'BUG')),
  status          VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED'
    CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'NONE')),
  position        INT          NOT NULL DEFAULT 0,
  assignee_id     UUID         REFERENCES tenant (id) ON DELETE SET NULL,
  branch_repo_id  UUID         REFERENCES repositories (id) ON DELETE SET NULL,
  branch_name     VARCHAR(255),
  linked_pr_id    UUID         REFERENCES pull_requests (id) ON DELETE SET NULL,
  created_at      TIMESTAMP WITHOUT TIME ZONE,
  updated_at      TIMESTAMP WITHOUT TIME ZONE,

  UNIQUE (project_id, code)
);

CREATE INDEX IF NOT EXISTS idx_tasks_project_id ON tasks (project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_board_column_id ON tasks (board_column_id);
CREATE INDEX IF NOT EXISTS idx_tasks_branch ON tasks (branch_repo_id, branch_name);

CREATE TABLE IF NOT EXISTS subtasks
(
  id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
  task_id     UUID         NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
  title       VARCHAR(255) NOT NULL,
  description TEXT,
  status      VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED'
    CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'NONE')),
  position    INT          NOT NULL DEFAULT 0,
  created_at  TIMESTAMP WITHOUT TIME ZONE,
  updated_at  TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_subtasks_task_id ON subtasks (task_id);

ALTER TABLE projects
  ADD COLUMN IF NOT EXISTS sync_task_status_on_pr_merge BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE tasks
  ADD COLUMN IF NOT EXISTS subtask_seq BIGINT NOT NULL DEFAULT 0;

ALTER TABLE subtasks
  ADD COLUMN IF NOT EXISTS code VARCHAR(20);

ALTER TABLE subtasks
  ADD COLUMN IF NOT EXISTS branch_repo_id UUID REFERENCES repositories (id) ON DELETE SET NULL;

ALTER TABLE subtasks
  ADD COLUMN IF NOT EXISTS branch_name VARCHAR(255);

ALTER TABLE subtasks
  ADD COLUMN IF NOT EXISTS linked_pr_id UUID REFERENCES pull_requests (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_subtasks_branch ON subtasks (branch_repo_id, branch_name);

WITH ranked AS (SELECT id,
                       ROW_NUMBER() OVER (PARTITION BY task_id ORDER BY created_at NULLS LAST, id) AS n
                FROM subtasks)
UPDATE subtasks s
SET code = 'SUB-' || ranked.n
FROM ranked
WHERE s.id = ranked.id;

ALTER TABLE subtasks
  ALTER COLUMN code SET NOT NULL;

ALTER TABLE subtasks
  ADD CONSTRAINT uq_subtasks_task_code UNIQUE (task_id, code);

ALTER TABLE repositories
  ADD COLUMN delete_head_branch_on_pr_merge BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN delete_head_branch_on_pr_close BOOLEAN NOT NULL DEFAULT FALSE;
