ALTER TABLE file_comments
  ADD COLUMN parent_id UUID REFERENCES file_comments(id) ON DELETE CASCADE;
CREATE INDEX idx_file_comments_parent ON file_comments(parent_id);
