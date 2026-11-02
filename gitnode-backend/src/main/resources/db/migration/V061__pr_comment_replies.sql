ALTER TABLE pull_request_comments
  ADD COLUMN parent_id UUID REFERENCES pull_request_comments(id) ON DELETE CASCADE;
CREATE INDEX idx_pr_comments_parent ON pull_request_comments(parent_id);
