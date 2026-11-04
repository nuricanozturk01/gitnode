-- Runners now always advertise self-hosted, their OS and architecture (lower-case), so that
-- `runs-on: ubuntu-latest` (translated to self-hosted + linux) can find them.
UPDATE runner
SET labels = ARRAY(
  SELECT DISTINCT lower(btrim(l))
  FROM unnest(
    labels
    || ARRAY['self-hosted']
    || CASE lower(coalesce(os, ''))
         WHEN 'linux' THEN ARRAY['linux']
         WHEN 'darwin' THEN ARRAY['macos']
         WHEN 'macos' THEN ARRAY['macos']
         WHEN 'windows' THEN ARRAY['windows']
         ELSE ARRAY[]::text[]
       END
    || CASE lower(coalesce(arch, ''))
         WHEN 'amd64' THEN ARRAY['x64']
         WHEN 'x86_64' THEN ARRAY['x64']
         WHEN 'x64' THEN ARRAY['x64']
         WHEN 'arm64' THEN ARRAY['arm64']
         WHEN 'aarch64' THEN ARRAY['arm64']
         ELSE ARRAY[]::text[]
       END
  ) AS l
  WHERE btrim(l) <> ''
);
