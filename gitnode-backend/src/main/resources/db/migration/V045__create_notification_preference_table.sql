CREATE TABLE notification_preference
(
    id        UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID        NOT NULL,
    type      VARCHAR(60) NOT NULL,
    enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_notification_preference PRIMARY KEY (id),
    CONSTRAINT fk_notif_pref_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    CONSTRAINT uq_notif_pref UNIQUE (tenant_id, type)
);

CREATE INDEX idx_notif_pref_tenant ON notification_preference (tenant_id);
