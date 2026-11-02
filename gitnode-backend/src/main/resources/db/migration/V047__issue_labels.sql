CREATE TABLE issue_labels (
  id UUID PRIMARY KEY,
  repo_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  name VARCHAR(50) NOT NULL,
  color VARCHAR(6) NOT NULL,
  description VARCHAR(255)
);

CREATE UNIQUE INDEX idx_issue_labels_repo_name ON issue_labels(repo_id, lower(name));

CREATE TABLE issue_label_assignments (
  issue_id UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
  label_id UUID NOT NULL REFERENCES issue_labels(id) ON DELETE CASCADE,
  PRIMARY KEY (issue_id, label_id)
);
