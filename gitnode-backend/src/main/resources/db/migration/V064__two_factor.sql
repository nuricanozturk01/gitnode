ALTER TABLE tenant ADD COLUMN totp_secret TEXT;
ALTER TABLE tenant ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenant ADD COLUMN totp_recovery_codes TEXT;
ALTER TABLE tenant ADD COLUMN totp_last_step BIGINT;
