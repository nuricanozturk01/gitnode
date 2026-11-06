-- Repositories and accounts own these rows, but the columns had no foreign key, so deleting a
-- repository or an account left them behind. Remove what is already orphaned, then let the
-- database cascade from now on (same as every other table that hangs off tenant/repositories).

DELETE FROM workflow_run WHERE repo_id NOT IN (SELECT id FROM repositories);
DELETE FROM workflow_definition WHERE repo_id NOT IN (SELECT id FROM repositories);
DELETE FROM workflow_cache WHERE repo_id NOT IN (SELECT id FROM repositories);
DELETE FROM workflow_secret WHERE repo_id IS NOT NULL AND repo_id NOT IN (SELECT id FROM repositories);
DELETE FROM webhook_dead_letters WHERE webhook_id NOT IN (SELECT id FROM webhooks);
DELETE FROM runner_registration_token
 WHERE created_by NOT IN (SELECT id FROM tenant)
    OR (tenant_id IS NOT NULL AND tenant_id NOT IN (SELECT id FROM tenant));
DELETE FROM runner WHERE tenant_id IS NOT NULL AND tenant_id NOT IN (SELECT id FROM tenant);
UPDATE runner SET created_by = NULL
 WHERE created_by IS NOT NULL AND created_by NOT IN (SELECT id FROM tenant);
UPDATE workflow_run SET trigger_actor = NULL
 WHERE trigger_actor IS NOT NULL AND trigger_actor NOT IN (SELECT id FROM tenant);

-- Cascades and SET NULL need an index on the referencing column or every delete scans the table.
CREATE INDEX IF NOT EXISTS idx_runner_created_by ON runner (created_by) WHERE created_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_runner_registration_token_tenant ON runner_registration_token (tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_runner_registration_token_creator ON runner_registration_token (created_by);
CREATE INDEX IF NOT EXISTS idx_workflow_run_trigger_actor ON workflow_run (trigger_actor) WHERE trigger_actor IS NOT NULL;

ALTER TABLE workflow_definition
    ADD CONSTRAINT fk_workflow_definition__repo_id
    FOREIGN KEY (repo_id) REFERENCES repositories (id) ON DELETE CASCADE;
ALTER TABLE workflow_run
    ADD CONSTRAINT fk_workflow_run__repo_id
    FOREIGN KEY (repo_id) REFERENCES repositories (id) ON DELETE CASCADE;
ALTER TABLE workflow_run
    ADD CONSTRAINT fk_workflow_run__trigger_actor
    FOREIGN KEY (trigger_actor) REFERENCES tenant (id) ON DELETE SET NULL;
ALTER TABLE workflow_cache
    ADD CONSTRAINT fk_workflow_cache__repo_id
    FOREIGN KEY (repo_id) REFERENCES repositories (id) ON DELETE CASCADE;
ALTER TABLE workflow_secret
    ADD CONSTRAINT fk_workflow_secret__repo_id
    FOREIGN KEY (repo_id) REFERENCES repositories (id) ON DELETE CASCADE;
ALTER TABLE webhook_dead_letters
    ADD CONSTRAINT fk_webhook_dead_letters__webhook_id
    FOREIGN KEY (webhook_id) REFERENCES webhooks (id) ON DELETE CASCADE;
ALTER TABLE runner
    ADD CONSTRAINT fk_runner__tenant_id
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE;
ALTER TABLE runner
    ADD CONSTRAINT fk_runner__created_by
    FOREIGN KEY (created_by) REFERENCES tenant (id) ON DELETE SET NULL;
ALTER TABLE runner_registration_token
    ADD CONSTRAINT fk_runner_registration_token__tenant_id
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE;
ALTER TABLE runner_registration_token
    ADD CONSTRAINT fk_runner_registration_token__created_by
    FOREIGN KEY (created_by) REFERENCES tenant (id) ON DELETE CASCADE;
