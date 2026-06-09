-- Admin dashboard: tenant/repo growth queries and organization email-domain SSO lookup

CREATE INDEX IF NOT EXISTS idx_tenant_created_at ON tenant (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_tenant_enabled ON tenant (enabled);

CREATE INDEX IF NOT EXISTS idx_repositories_created_at ON repositories (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_organization_email_domains ON organization USING GIN (email_domains);
