CREATE TABLE release_assets (
  id UUID PRIMARY KEY,
  release_id UUID NOT NULL REFERENCES releases(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  content_type VARCHAR(255) NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
  sha256 VARCHAR(64) NOT NULL,
  uploader_id UUID REFERENCES tenant(id) ON DELETE SET NULL,
  download_count BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX uq_release_assets_name ON release_assets(release_id, lower(name));
