CREATE TABLE discussions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  repo_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  number INT NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT,
  category VARCHAR(20) NOT NULL
    CHECK (category IN ('ANNOUNCEMENTS', 'GENERAL', 'IDEAS', 'Q_AND_A', 'SHOW_AND_TELL')),
  author_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  locked BOOLEAN NOT NULL DEFAULT FALSE,
  answer_comment_id UUID,
  created_at TIMESTAMP WITH TIME ZONE,
  updated_at TIMESTAMP WITH TIME ZONE,
  UNIQUE (repo_id, number)
);
CREATE INDEX idx_discussions_repo_created ON discussions(repo_id, created_at DESC);
CREATE INDEX idx_discussions_repo_category ON discussions(repo_id, category);

CREATE TABLE discussion_comments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  discussion_id UUID NOT NULL REFERENCES discussions(id) ON DELETE CASCADE,
  parent_id UUID REFERENCES discussion_comments(id) ON DELETE CASCADE,
  author_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  body TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE,
  updated_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_discussion_comments_discussion ON discussion_comments(discussion_id, created_at);

ALTER TABLE discussions
  ADD CONSTRAINT fk_discussions_answer
  FOREIGN KEY (answer_comment_id) REFERENCES discussion_comments(id) ON DELETE SET NULL;

CREATE TABLE discussion_votes (
  discussion_id UUID NOT NULL REFERENCES discussions(id) ON DELETE CASCADE,
  tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  PRIMARY KEY (discussion_id, tenant_id)
);
