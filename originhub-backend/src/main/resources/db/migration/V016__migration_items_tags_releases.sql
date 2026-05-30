ALTER TABLE migration_jobs_migration_items
    DROP CONSTRAINT IF EXISTS migration_jobs_migration_items_migration_items_check;

ALTER TABLE migration_jobs_migration_items
    ADD CONSTRAINT migration_jobs_migration_items_migration_items_check
        CHECK (migration_items IN ('REPOSITORIES', 'PULL_REQUESTS', 'TAGS_AND_RELEASES'));
