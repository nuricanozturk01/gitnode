-- A release can be pinned as "latest"; at most one per repository.
ALTER TABLE releases ADD COLUMN is_latest BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uq_releases_one_latest ON releases(repo_id) WHERE is_latest;
