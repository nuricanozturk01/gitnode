CREATE TABLE runner_group (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id      BIGINT NOT NULL,
    name        VARCHAR(128) NOT NULL,
    labels      TEXT[] NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (org_id, name)
);

CREATE INDEX idx_runner_group_org ON runner_group(org_id);

ALTER TABLE runner ADD COLUMN IF NOT EXISTS group_id BIGINT REFERENCES runner_group(id) ON DELETE SET NULL;
