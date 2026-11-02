-- Labels, assignees and a milestone for pull requests, reusing the repository's issue labels/milestones.
CREATE TABLE pr_label_assignments (
  pr_id UUID NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
  label_id UUID NOT NULL REFERENCES issue_labels(id) ON DELETE CASCADE,
  PRIMARY KEY (pr_id, label_id)
);
CREATE TABLE pr_assignees (
  pr_id UUID NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
  tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  PRIMARY KEY (pr_id, tenant_id)
);
ALTER TABLE pull_requests ADD COLUMN milestone_id UUID REFERENCES issue_milestones(id) ON DELETE SET NULL;
CREATE INDEX idx_pull_requests_milestone ON pull_requests(milestone_id);
