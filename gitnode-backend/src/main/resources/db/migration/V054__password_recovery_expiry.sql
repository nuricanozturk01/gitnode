-- Recovery codes are now stored hashed and expire; invalidate every legacy plain-text code.
UPDATE tenant SET password_recovery_code = NULL WHERE password_recovery_code IS NOT NULL;
ALTER TABLE tenant ADD COLUMN password_recovery_expires_at TIMESTAMP WITH TIME ZONE;
