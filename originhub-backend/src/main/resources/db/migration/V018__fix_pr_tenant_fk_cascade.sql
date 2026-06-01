ALTER TABLE pull_requests
  DROP CONSTRAINT IF EXISTS pull_requests_author_id_fkey,
  ADD CONSTRAINT pull_requests_author_id_fkey
    FOREIGN KEY (author_id) REFERENCES tenant (id) ON DELETE CASCADE;

ALTER TABLE pull_requests
  DROP CONSTRAINT IF EXISTS pull_requests_merged_by_id_fkey,
  ADD CONSTRAINT pull_requests_merged_by_id_fkey
    FOREIGN KEY (merged_by_id) REFERENCES tenant (id) ON DELETE SET NULL;

ALTER TABLE pull_request_comments
  DROP CONSTRAINT IF EXISTS pull_request_comments_author_id_fkey,
  ADD CONSTRAINT pull_request_comments_author_id_fkey
    FOREIGN KEY (author_id) REFERENCES tenant (id) ON DELETE CASCADE;
