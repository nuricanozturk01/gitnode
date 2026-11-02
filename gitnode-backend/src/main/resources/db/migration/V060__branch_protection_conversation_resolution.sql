ALTER TABLE branch_protection_rules
  ADD COLUMN require_conversation_resolution BOOLEAN NOT NULL DEFAULT FALSE;
