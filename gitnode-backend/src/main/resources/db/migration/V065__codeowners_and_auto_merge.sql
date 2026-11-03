ALTER TABLE branch_protection_rules
  ADD COLUMN require_code_owner_review BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE pull_requests
  ADD COLUMN auto_merge_strategy VARCHAR(20),
  ADD COLUMN auto_merge_by_id UUID REFERENCES tenant(id) ON DELETE SET NULL;

CREATE INDEX idx_pull_requests_auto_merge
  ON pull_requests(id) WHERE auto_merge_strategy IS NOT NULL AND status = 'OPEN';
