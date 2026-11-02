CREATE TABLE pr_reviews (
  id UUID PRIMARY KEY,
  pr_id UUID NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
  author_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  state VARCHAR(20) NOT NULL CHECK (state IN ('APPROVED', 'CHANGES_REQUESTED', 'COMMENTED')),
  body TEXT,
  commit_sha VARCHAR(40) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_pr_reviews_pr_created ON pr_reviews(pr_id, created_at);
