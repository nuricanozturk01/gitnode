CREATE TABLE releases
(
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    repo_id       UUID         NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
    author_id     UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    tag_name      VARCHAR(255) NOT NULL,
    name          VARCHAR(255),
    body          TEXT,
    is_draft      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_prerelease BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at  TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_release_repo_tag UNIQUE (repo_id, tag_name)
);

CREATE INDEX idx_releases_repo_id ON releases (repo_id);
CREATE INDEX idx_releases_published_at ON releases (repo_id, is_draft, is_prerelease, published_at DESC);
