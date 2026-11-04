CREATE TABLE team_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  member_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  role VARCHAR(20) NOT NULL CHECK (role IN ('VIEWER', 'DEVELOPER', 'MAINTAINER', 'ADMIN')),
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED')),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  accepted_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uk_team_members_owner_member UNIQUE (owner_id, member_id),
  CONSTRAINT ck_team_members_not_self CHECK (owner_id <> member_id)
);
CREATE INDEX idx_team_members_owner_status_created ON team_members(owner_id, status, created_at DESC);
CREATE INDEX idx_team_members_member_status ON team_members(member_id, status);
