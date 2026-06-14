CREATE TABLE user_ai_settings
(
  id         UUID        NOT NULL DEFAULT gen_random_uuid(),
  tenant_id  UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  provider   VARCHAR(20) NOT NULL DEFAULT 'OPENAI',
  api_key    TEXT,
  api_key_iv TEXT,
  base_url   VARCHAR(500),
  model      VARCHAR(100),
  enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT pk_user_ai_settings PRIMARY KEY (id),
  CONSTRAINT uq_user_ai_settings_tenant UNIQUE (tenant_id)
);

CREATE INDEX idx_user_ai_settings_tenant ON user_ai_settings (tenant_id);

CREATE TABLE ai_code_reviews
(
  id             UUID        NOT NULL DEFAULT gen_random_uuid(),
  repo_id        UUID        NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
  pr_number      INTEGER     NOT NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  summary        TEXT,
  status_message TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT pk_ai_code_reviews PRIMARY KEY (id)
);

CREATE TABLE ai_code_review_comments
(
  id          UUID         NOT NULL DEFAULT gen_random_uuid(),
  review_id   UUID         NOT NULL REFERENCES ai_code_reviews (id) ON DELETE CASCADE,
  file_path   VARCHAR(500) NOT NULL,
  line_number INTEGER,
  category    VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
  severity    VARCHAR(20)  NOT NULL DEFAULT 'INFO',
  suggestion  TEXT,
  comment     TEXT         NOT NULL,
  CONSTRAINT pk_ai_code_review_comments PRIMARY KEY (id)
);

CREATE INDEX idx_ai_code_reviews_repo_pr ON ai_code_reviews (repo_id, pr_number);
CREATE INDEX idx_ai_code_review_comments_review ON ai_code_review_comments (review_id);

CREATE TABLE ai_codebase_analyses
(
  id                UUID         NOT NULL DEFAULT gen_random_uuid(),
  repo_id           UUID         NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
  branch            VARCHAR(255) NOT NULL,
  status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  arch_score        SMALLINT,
  quality_score     SMALLINT,
  perf_score        SMALLINT,
  memory_score      SMALLINT,
  scalability_score SMALLINT,
  security_score    SMALLINT,
  overall_score     SMALLINT,
  summary           TEXT,
  recommendations   TEXT,
  raw_result        TEXT,
  dimension_details TEXT,
  triggered_by      UUID         REFERENCES tenant (id) ON DELETE SET NULL,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT pk_ai_codebase_analyses PRIMARY KEY (id)
);

CREATE INDEX idx_ai_codebase_analyses_repo ON ai_codebase_analyses (repo_id, created_at DESC);

ALTER TABLE repositories
  ADD COLUMN ai_pr_review_enabled BOOLEAN NOT NULL DEFAULT FALSE;
