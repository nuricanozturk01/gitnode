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

export type NotificationType =
  | 'ISSUE_COMMENT'
  | 'PR_COMMENT'
  | 'PR_MERGED'
  | 'PR_CLOSED'
  | 'AI_CODE_REVIEW_COMPLETED'
  | 'AI_CODE_REVIEW_FAILED'
  | 'AI_ANALYSIS_COMPLETED'
  | 'AI_ANALYSIS_FAILED'
  | 'COLLABORATOR_INVITED';

export interface NotificationDto {
  id: string;
  type: NotificationType;
  title: string;
  body: string | null;
  link: string | null;
  read: boolean;
  actorId: string | null;
  entityId: string | null;
  createdAt: string;
}

export interface NotificationPage {
  content: NotificationDto[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface NotificationPreference {
  type: NotificationType;
  enabled: boolean;
}

export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  ISSUE_COMMENT: 'Issue comments',
  PR_COMMENT: 'Pull request comments',
  PR_MERGED: 'Pull request merged',
  PR_CLOSED: 'Pull request closed',
  AI_CODE_REVIEW_COMPLETED: 'AI code review completed',
  AI_CODE_REVIEW_FAILED: 'AI code review failed',
  AI_ANALYSIS_COMPLETED: 'AI codebase analysis completed',
  AI_ANALYSIS_FAILED: 'AI codebase analysis failed',
  COLLABORATOR_INVITED: 'Collaboration invitations',
};
