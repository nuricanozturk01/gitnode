-- One row per non-merge commit on a repository's default branch that is authored by a known account.
CREATE TABLE commit_contributions (
  repo_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  sha VARCHAR(40) NOT NULL,
  author_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  committed_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (repo_id, sha)
);
CREATE INDEX idx_commit_contributions_author_time ON commit_contributions(author_id, committed_at);

-- The default-branch head that was last indexed, so each push only reads its new commits.
CREATE TABLE commit_index_state (
  repo_id UUID PRIMARY KEY REFERENCES repositories(id) ON DELETE CASCADE,
  indexed_sha VARCHAR(40) NOT NULL
);
