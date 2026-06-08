CREATE TABLE project_repos (
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    repo_id    UUID NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, repo_id)
);

INSERT INTO project_repos (project_id, repo_id)
SELECT project_id, id FROM repositories WHERE project_id IS NOT NULL;

DROP INDEX IF EXISTS idx_repos_project_id;
ALTER TABLE repositories DROP COLUMN IF EXISTS project_id;
