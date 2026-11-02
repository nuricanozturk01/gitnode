CREATE TABLE issue_milestones (
  id UUID PRIMARY KEY,
  repo_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  description VARCHAR(2000),
  due_date DATE,
  status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'CLOSED'))
);
CREATE UNIQUE INDEX idx_issue_milestones_repo_title ON issue_milestones(repo_id, lower(title));
ALTER TABLE issues ADD COLUMN milestone_id UUID REFERENCES issue_milestones(id) ON DELETE SET NULL;
CREATE INDEX idx_issues_milestone ON issues(milestone_id);
