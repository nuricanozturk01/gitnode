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

export interface WebhookInfo {
  id: string;
  url: string;
  enabled: boolean;
  events: string[];
  createdAt: string;
  updatedAt: string;
}

export interface WebhookForm {
  url: string;
  secret?: string;
  enabled: boolean;
  events: string[];
}

export interface WebhookUpdateForm {
  url?: string;
  secret?: string;
  enabled?: boolean;
  events?: string[];
}

export interface WebhookEventGroup {
  label: string;
  events: { key: string; label: string }[];
}

export const WEBHOOK_EVENT_GROUPS: WebhookEventGroup[] = [
  {
    label: 'Repository',
    events: [
      { key: 'REPO_CREATED', label: 'Created' },
      { key: 'REPO_DELETED', label: 'Deleted' },
      { key: 'REPO_UPDATED', label: 'Updated' },
      { key: 'REPO_PUSHED', label: 'Pushed' },
    ],
  },
  {
    label: 'Branch',
    events: [
      { key: 'BRANCH_CREATED', label: 'Created' },
      { key: 'BRANCH_DELETED', label: 'Deleted' },
    ],
  },
  {
    label: 'Pull Requests',
    events: [
      { key: 'PULL_REQUEST_OPENED', label: 'Opened' },
      { key: 'PULL_REQUEST_CLOSED', label: 'Closed' },
      { key: 'PULL_REQUEST_MERGED', label: 'Merged' },
      { key: 'PULL_REQUEST_UPDATED', label: 'Updated' },
    ],
  },
  {
    label: 'Issues',
    events: [
      { key: 'ISSUE_OPENED', label: 'Opened' },
      { key: 'ISSUE_CLOSED', label: 'Closed' },
      { key: 'ISSUE_REOPENED', label: 'Reopened' },
      { key: 'ISSUE_UPDATED', label: 'Updated' },
      { key: 'ISSUE_COMMENTED', label: 'Commented' },
    ],
  },
];

export const USER_WEBHOOK_EVENT_GROUPS: WebhookEventGroup[] = [
  {
    label: 'Projects',
    events: [
      { key: 'PROJECT_CREATED', label: 'Created' },
      { key: 'PROJECT_DELETED', label: 'Deleted' },
      { key: 'PROJECT_UPDATED', label: 'Updated' },
    ],
  },
  {
    label: 'Snippets',
    events: [
      { key: 'SNIPPET_CREATED', label: 'Created' },
      { key: 'SNIPPET_DELETED', label: 'Deleted' },
      { key: 'SNIPPET_UPDATED', label: 'Updated' },
    ],
  },
];

export const PROJECT_WEBHOOK_EVENT_GROUPS: WebhookEventGroup[] = [
  {
    label: 'Tasks',
    events: [
      { key: 'TASK_CREATED', label: 'Created' },
      { key: 'TASK_DELETED', label: 'Deleted' },
      { key: 'TASK_UPDATED', label: 'Updated' },
    ],
  },
];
