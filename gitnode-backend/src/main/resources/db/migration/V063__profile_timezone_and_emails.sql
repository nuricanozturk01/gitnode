ALTER TABLE tenant ADD COLUMN timezone VARCHAR(64);

-- Extra commit e-mail addresses that attribute commits to an account for the contribution graph.
CREATE TABLE tenant_emails (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  email VARCHAR(254) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_tenant_emails_email ON tenant_emails (lower(email));
CREATE INDEX idx_tenant_emails_tenant ON tenant_emails (tenant_id);
