CREATE TABLE IF NOT EXISTS organization
(
  id                  UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
  name                VARCHAR(255)            NOT NULL,
  slug                VARCHAR(100)            NOT NULL UNIQUE,
  email_domains       TEXT[]                  NOT NULL,
  sso_enabled         BOOLEAN                 NOT NULL DEFAULT FALSE,
  idp_metadata_uri    TEXT,
  idp_metadata_xml    TEXT,
  email_attribute     VARCHAR(100)            NOT NULL DEFAULT 'email',
  username_attribute  VARCHAR(100),
  sp_entity_id        VARCHAR(255),
  created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_organization_slug ON organization (slug);
CREATE INDEX IF NOT EXISTS idx_organization_sso_enabled ON organization (sso_enabled);
