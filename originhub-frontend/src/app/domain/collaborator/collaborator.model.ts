///
/// Copyright 2026 the original author or authors.
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///      https://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

export type CollaboratorPermission =
  | 'READ'
  | 'PUSH'
  | 'PULL_REQUEST_CREATE'
  | 'PULL_REQUEST_REVIEW'
  | 'PULL_REQUEST_MERGE'
  | 'ISSUE_MANAGE'
  | 'SETTINGS_READ'
  | 'SETTINGS_WRITE'
  | 'ADMIN';

export type CollaboratorStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED';

export interface CollaboratorInfo {
  id: string;
  username: string;
  displayName: string | null;
  avatarUrl: string;
  permissions: CollaboratorPermission[];
  status: CollaboratorStatus;
  invitedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CollaboratorPage {
  content: CollaboratorInfo[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface InviteCollaboratorForm {
  username: string;
  permissions: CollaboratorPermission[];
}

export interface UpdateCollaboratorPermissionsForm {
  permissions: CollaboratorPermission[];
}

export interface InviteLinkResponse {
  token: string;
  expiresAt: string;
  repoOwner: string;
  repoName: string;
  inviteeUsername: string;
}

export interface InvitationTokenInfo {
  repoOwner: string;
  repoName: string;
  invitedUsername: string;
  invitedBy: string;
  permissions: CollaboratorPermission[];
  expiresAt: string;
}

/** Grouped for the collaborator "My Permissions" overview. */
export interface CollaboratorPermissionGroup {
  id: string;
  title: string;
  summary: string;
  icon: string;
  permissions: CollaboratorPermission[];
}

export const COLLABORATOR_PERMISSION_GROUPS: CollaboratorPermissionGroup[] = [
  {
    id: 'code',
    title: 'Code access',
    summary: 'Clone, browse, and push to this repository',
    icon: 'folder',
    permissions: ['READ', 'PUSH'],
  },
  {
    id: 'pull-requests',
    title: 'Pull requests',
    summary: 'Create, review, and merge pull requests',
    icon: 'gitPullRequest',
    permissions: ['PULL_REQUEST_CREATE', 'PULL_REQUEST_REVIEW', 'PULL_REQUEST_MERGE'],
  },
  {
    id: 'issues',
    title: 'Issues',
    summary: 'Manage issues on this repository',
    icon: 'circleDot',
    permissions: ['ISSUE_MANAGE'],
  },
  {
    id: 'settings',
    title: 'Settings',
    summary: 'View or change repository settings and webhooks',
    icon: 'settings',
    permissions: ['SETTINGS_READ', 'SETTINGS_WRITE'],
  },
  {
    id: 'admin',
    title: 'Administration',
    summary: 'Full collaborator and settings control',
    icon: 'shield',
    permissions: ['ADMIN'],
  },
];

export const ALL_PERMISSIONS: { value: CollaboratorPermission; label: string; description: string }[] = [
  { value: 'READ', label: 'Read', description: 'View private repository contents' },
  { value: 'PUSH', label: 'Push', description: 'Push commits to non-protected branches' },
  { value: 'PULL_REQUEST_CREATE', label: 'Create PRs', description: 'Open pull requests' },
  { value: 'PULL_REQUEST_REVIEW', label: 'Review PRs', description: 'Review and approve pull requests' },
  { value: 'PULL_REQUEST_MERGE', label: 'Merge PRs', description: 'Merge pull requests' },
  { value: 'ISSUE_MANAGE', label: 'Manage Issues', description: 'Create, edit, and close any issue' },
  { value: 'SETTINGS_READ', label: 'Read Settings', description: 'View repository settings' },
  { value: 'SETTINGS_WRITE', label: 'Write Settings', description: 'Modify repository settings' },
  { value: 'ADMIN', label: 'Admin', description: 'Full access including collaborator management' },
];
