CREATE TABLE pr_reviewers (
  pr_id UUID NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
  tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  PRIMARY KEY (pr_id, tenant_id)
);
