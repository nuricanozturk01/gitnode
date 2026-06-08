CREATE TABLE IF NOT EXISTS repo_collaborators
(
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  repo_id       UUID        NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
  tenant_id     UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  invited_by_id UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
  created_at    TIMESTAMP WITHOUT TIME ZONE,
  updated_at    TIMESTAMP WITHOUT TIME ZONE,
  UNIQUE (repo_id, tenant_id)
);

CREATE TABLE IF NOT EXISTS repo_collaborator_permissions
(
  collaborator_id UUID        NOT NULL REFERENCES repo_collaborators (id) ON DELETE CASCADE,
  permission      VARCHAR(50) NOT NULL,
  PRIMARY KEY (collaborator_id, permission)
);

CREATE INDEX IF NOT EXISTS idx_repo_collaborators_repo_id ON repo_collaborators (repo_id);
CREATE INDEX IF NOT EXISTS idx_repo_collaborators_tenant_id ON repo_collaborators (tenant_id);
CREATE INDEX IF NOT EXISTS idx_repo_collaborators_status ON repo_collaborators (status);

ALTER TABLE repo_collaborators
    ADD COLUMN invite_token VARCHAR(36) UNIQUE,
    ADD COLUMN token_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_collab_invite_token ON repo_collaborators (invite_token) WHERE invite_token IS NOT NULL;
