CREATE INDEX IF NOT EXISTS idx_issues_author_created_at ON issues (author_id, created_at);

CREATE INDEX IF NOT EXISTS idx_issue_comments_author_created_at
  ON issue_comments (author_id, created_at);

CREATE INDEX IF NOT EXISTS idx_pull_requests_merged_by_merged_at
  ON pull_requests (merged_by_id, merged_at)
  WHERE merged_by_id IS NOT NULL AND merged_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_releases_author_published_at
  ON releases (author_id, published_at);

CREATE INDEX IF NOT EXISTS idx_snippet_revisions_author_created_at
  ON snippet_revisions (author_id, created_at);

CREATE INDEX IF NOT EXISTS idx_snippet_comments_author_created_at
  ON snippet_comments (author_id, created_at);
