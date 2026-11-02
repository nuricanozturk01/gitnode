CREATE TABLE issue_assignees
(
  issue_id UUID NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
  tenant_id UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  PRIMARY KEY (issue_id, tenant_id)
);

INSERT INTO issue_assignees (issue_id, tenant_id)
SELECT id, assignee_id FROM issues WHERE assignee_id IS NOT NULL;

CREATE INDEX idx_issue_assignees_tenant_id ON issue_assignees (tenant_id);
