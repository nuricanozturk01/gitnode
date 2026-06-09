CREATE TABLE IF NOT EXISTS snippets
(
  id             UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
  owner_id       UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  title          VARCHAR(200) NOT NULL,
  description    TEXT,
  visibility     VARCHAR(10)  NOT NULL DEFAULT 'PUBLIC'
    CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
  forked_from_id UUID         REFERENCES snippets (id) ON DELETE SET NULL,
  file_count     INTEGER      NOT NULL DEFAULT 0,
  comment_count  INTEGER      NOT NULL DEFAULT 0,
  fork_count     INTEGER      NOT NULL DEFAULT 0,
  created_at     TIMESTAMP WITHOUT TIME ZONE,
  updated_at     TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_snippets_owner_id ON snippets (owner_id);
CREATE INDEX IF NOT EXISTS idx_snippets_visibility_at ON snippets (visibility, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_snippets_forked_from_id ON snippets (forked_from_id);

CREATE TABLE IF NOT EXISTS snippet_files
(
  id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
  snippet_id UUID         NOT NULL REFERENCES snippets (id) ON DELETE CASCADE,
  filename   VARCHAR(255) NOT NULL,
  position   INTEGER      NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITHOUT TIME ZONE,
  updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_snippet_files_snippet_id ON snippet_files (snippet_id);

CREATE TABLE IF NOT EXISTS snippet_comments
(
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  snippet_id UUID NOT NULL REFERENCES snippets (id) ON DELETE CASCADE,
  author_id  UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  body       TEXT NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE,
  updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_snippet_comments_snippet_id ON snippet_comments (snippet_id, created_at);

CREATE TABLE IF NOT EXISTS snippet_revisions
(
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  snippet_id  UUID         NOT NULL REFERENCES snippets (id) ON DELETE CASCADE,
  author_id   UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  title       VARCHAR(200) NOT NULL,
  description TEXT,
  summary     VARCHAR(255),
  created_at  TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_snippet_revisions_snippet_id ON snippet_revisions (snippet_id, created_at DESC);

CREATE TABLE IF NOT EXISTS snippet_revision_files
(
  id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
  revision_id UUID         NOT NULL REFERENCES snippet_revisions (id) ON DELETE CASCADE,
  filename    VARCHAR(255) NOT NULL,
  position    INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_snippet_revision_files_revision_id ON snippet_revision_files (revision_id);
