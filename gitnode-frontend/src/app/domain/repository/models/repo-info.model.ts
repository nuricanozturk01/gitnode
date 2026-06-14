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

/**
 * API response for repository operations.
 * Matches backend RepoInfo DTO.
 */
export interface RepoInfoOwner {
  id: string;
  username: string;
  avatarUrl?: string | null;
}

export interface RepoForkedFromInfo {
  id: string;
  ownerUsername: string;
  name: string;
}

export interface RepoInfo {
  id: string;
  owner?: RepoInfoOwner | null;
  name: string;
  description: string | null;
  isPrivate: boolean;
  isArchived: boolean;
  defaultBranch: string;
  /** May be null/omitted from API for older rows. */
  topics?: string[] | null;
  /** When true, the PR head branch is removed after a successful merge. */
  deleteHeadBranchOnPrMerge?: boolean;
  /** When true, the PR head branch is removed when the PR is closed without merging. */
  deleteHeadBranchOnPrClose?: boolean;
  /** When true, new pull requests are reviewed automatically by AI. */
  aiPrReviewEnabled?: boolean;
  createdAt: string;
  updatedAt: string;
  forkedFrom?: RepoForkedFromInfo | null;
  forkCount?: number;
}
