CREATE TABLE snippet_repos (
    snippet_id UUID NOT NULL REFERENCES snippets (id) ON DELETE CASCADE,
    repo_id    UUID NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
    PRIMARY KEY (snippet_id, repo_id)
);

INSERT INTO snippet_repos (snippet_id, repo_id)
SELECT id, repo_id
FROM snippets
WHERE repo_id IS NOT NULL;

DROP INDEX IF EXISTS idx_snippets_repo_id;

ALTER TABLE snippets DROP COLUMN IF EXISTS repo_id;
