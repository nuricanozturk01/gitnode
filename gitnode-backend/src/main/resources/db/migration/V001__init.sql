CREATE TABLE IF NOT EXISTS tenant
(
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username               VARCHAR(80)  NOT NULL UNIQUE,
  email                  VARCHAR(255) NOT NULL UNIQUE,
  display_name           VARCHAR(100),
  hash                   VARCHAR(128),
  salt                   VARCHAR(16),
  avatar_url             VARCHAR(500),
  is_admin               BOOLEAN          DEFAULT FALSE,
  password_recovery_code VARCHAR(300),
  created_at             TIMESTAMP WITHOUT TIME ZONE,
  updated_at             TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE IF NOT EXISTS sso_account
(
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_type VARCHAR(256)                NOT NULL,
  subject_id   VARCHAR(256)                NOT NULL,
  username     VARCHAR(256),
  email        VARCHAR(256),
  avatar_url   TEXT,
  tenant_id    UUID                        NOT NULL,
  created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  CONSTRAINT fk_sso_account__tenant_id FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ssh_keys
(
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  title        VARCHAR(100) NOT NULL,
  public_key   TEXT         NOT NULL UNIQUE,
  fingerprint  VARCHAR(255) NOT NULL UNIQUE,
  last_used_at TIMESTAMP WITHOUT TIME ZONE,
  created_at   TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE IF NOT EXISTS repositories
(
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id       UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  name           VARCHAR(100) NOT NULL,
  description    TEXT,
  is_archived    BOOLEAN          DEFAULT FALSE,
  default_branch VARCHAR(255)     DEFAULT 'main',
  topics         TEXT[],
  created_at     TIMESTAMP WITHOUT TIME ZONE,
  updated_at     TIMESTAMP WITHOUT TIME ZONE,

  UNIQUE (owner_id, name)
);

CREATE TABLE IF NOT EXISTS pull_requests
(
  id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
  repo_id       UUID         NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
  number        INT          NOT NULL,
  title         VARCHAR(255) NOT NULL,
  description   TEXT,
  status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
    CHECK (status IN ('OPEN', 'MERGED', 'CLOSED')),
  author_id     UUID         NOT NULL REFERENCES tenant (id),
  merged_by_id  UUID REFERENCES tenant (id),
  source_branch VARCHAR(255) NOT NULL, -- feature/my-feature
  source_sha    VARCHAR(40),
  target_branch VARCHAR(255) NOT NULL,
  merge_sha     VARCHAR(40),
  is_draft      BOOLEAN               DEFAULT FALSE,
  created_at    TIMESTAMP WITHOUT TIME ZONE,
  updated_at    TIMESTAMP WITHOUT TIME ZONE,
  merged_at     TIMESTAMP WITHOUT TIME ZONE,
  closed_at     TIMESTAMP WITHOUT TIME ZONE,

  UNIQUE (repo_id, number)
);

CREATE TABLE IF NOT EXISTS pull_request_comments
(
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  pr_id       UUID NOT NULL REFERENCES pull_requests (id) ON DELETE CASCADE,
  author_id   UUID NOT NULL REFERENCES tenant (id),
  body        TEXT NOT NULL,
  file_path   VARCHAR(500),
  commit_sha  VARCHAR(40),
  line_number INT,
  line_side   VARCHAR(5) CHECK (line_side IN ('LEFT', 'RIGHT')),
  is_resolved BOOLEAN          DEFAULT FALSE,
  created_at  TIMESTAMP WITHOUT TIME ZONE,
  updated_at  TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID                     NOT NULL PRIMARY KEY,
  listener_id            TEXT                     NOT NULL,
  event_type             TEXT                     NOT NULL,
  serialized_event       TEXT                     NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INTEGER,
  last_resubmission_date TIMESTAMP WITH TIME ZONE
);

CREATE INDEX event_publication_serialized_event_hash_idx
  ON event_publication USING hash (serialized_event);

CREATE INDEX event_publication_by_completion_date_idx
  ON event_publication (completion_date);
