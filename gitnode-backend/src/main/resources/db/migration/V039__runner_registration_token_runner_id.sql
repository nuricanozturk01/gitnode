ALTER TABLE runner_registration_token
    ADD COLUMN runner_id UUID REFERENCES runner (id) ON DELETE SET NULL;
