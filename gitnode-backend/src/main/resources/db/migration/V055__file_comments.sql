CREATE TABLE file_comments (
  id UUID PRIMARY KEY,
  repo_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  commit_sha VARCHAR(40) NOT NULL,
  path TEXT NOT NULL,
  line INTEGER CHECK (line IS NULL OR line > 0),
  body TEXT NOT NULL,
  author_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_file_comments_repo_path ON file_comments(repo_id, path, created_at);
CREATE INDEX idx_file_comments_author ON file_comments(author_id);
