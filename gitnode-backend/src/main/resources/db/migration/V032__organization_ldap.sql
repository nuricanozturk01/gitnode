ALTER TABLE organization
  ADD COLUMN IF NOT EXISTS ldap_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS ldap_url TEXT,
  ADD COLUMN IF NOT EXISTS ldap_base_dn VARCHAR(512),
  ADD COLUMN IF NOT EXISTS ldap_manager_dn VARCHAR(512),
  ADD COLUMN IF NOT EXISTS ldap_manager_password TEXT,
  ADD COLUMN IF NOT EXISTS ldap_user_search_base VARCHAR(255) NOT NULL DEFAULT 'ou=people',
  ADD COLUMN IF NOT EXISTS ldap_user_search_filter VARCHAR(255) NOT NULL DEFAULT '(uid={0})',
  ADD COLUMN IF NOT EXISTS ldap_email_attribute VARCHAR(100) NOT NULL DEFAULT 'mail',
  ADD COLUMN IF NOT EXISTS ldap_display_name_attribute VARCHAR(100) NOT NULL DEFAULT 'cn',
  ADD COLUMN IF NOT EXISTS ldap_use_start_tls BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS ldap_group_search_base VARCHAR(255) DEFAULT 'ou=groups',
  ADD COLUMN IF NOT EXISTS ldap_group_search_filter VARCHAR(255) DEFAULT '(memberUid={0})',
  ADD COLUMN IF NOT EXISTS ldap_group_role_attribute VARCHAR(100) DEFAULT 'cn',
  ADD COLUMN IF NOT EXISTS ldap_admin_group_dns TEXT;

CREATE INDEX IF NOT EXISTS idx_organization_ldap_enabled ON organization (ldap_enabled);
