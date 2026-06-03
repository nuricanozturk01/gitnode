ALTER TABLE repositories
    ADD COLUMN forked_from_id UUID REFERENCES repositories (id) ON DELETE SET NULL,
    ADD COLUMN fork_count     INT NOT NULL DEFAULT 0;
