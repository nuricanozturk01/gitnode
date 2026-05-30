ALTER TABLE snippets
  ADD COLUMN IF NOT EXISTS repo_id UUID REFERENCES repositories (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_snippets_repo_id ON snippets (repo_id);
