CREATE TABLE notification
(
    id           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    recipient_id UUID                     NOT NULL,
    actor_id     UUID,
    type         VARCHAR(60)              NOT NULL,
    title        VARCHAR(255)             NOT NULL,
    body         TEXT,
    link         VARCHAR(500),
    is_read      BOOLEAN                  NOT NULL DEFAULT FALSE,
    entity_id    UUID,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES tenant (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_actor FOREIGN KEY (actor_id) REFERENCES tenant (id) ON DELETE SET NULL
);

CREATE INDEX idx_notification_recipient_unread ON notification (recipient_id, is_read, created_at DESC);
CREATE INDEX idx_notification_recipient_all ON notification (recipient_id, created_at DESC);
