CREATE INDEX IF NOT EXISTS idx_sso_account_type ON sso_account (account_type);
CREATE INDEX IF NOT EXISTS idx_sso_account_subject ON sso_account (subject_id, account_type);
