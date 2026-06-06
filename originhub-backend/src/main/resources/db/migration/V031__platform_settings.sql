CREATE TABLE platform_setting (
    setting_key   VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(512) NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO platform_setting (setting_key, setting_value)
VALUES ('stats_cache_ttl_seconds', '300');
