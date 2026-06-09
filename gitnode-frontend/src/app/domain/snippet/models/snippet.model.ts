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

export type SnippetVisibility = 'PUBLIC' | 'PRIVATE';

export interface SnippetOwnerInfo {
  id: string;
  username: string;
  email: string;
  avatarUrl: string | null;
}

export interface SnippetFileInfo {
  id: string;
  filename: string;
  content: string;
  position: number;
}

export interface SnippetFileForm {
  filename: string;
  content: string;
}

export interface SnippetForkedFromInfo {
  id: string;
  title: string;
  owner: SnippetOwnerInfo;
}

export interface SnippetLinkedRepoInfo {
  id: string;
  name: string;
}

export interface SnippetInfo {
  id: string;
  title: string;
  description: string | null;
  visibility: SnippetVisibility;
  owner: SnippetOwnerInfo;
  fileCount: number;
  commentCount: number;
  forkCount: number;
  forkedFrom: SnippetForkedFromInfo | null;
  repos: SnippetLinkedRepoInfo[];
  createdAt: string | null;
  updatedAt: string | null;
}

export interface SnippetDetail extends SnippetInfo {
  files: SnippetFileInfo[];
}

export interface SnippetForm {
  title: string;
  description?: string;
  visibility: SnippetVisibility;
  files: SnippetFileForm[];
}

export interface SnippetUpdateForm {
  title?: string;
  description?: string;
  visibility?: SnippetVisibility;
  files?: SnippetFileForm[];
  summary?: string;
}

export interface SnippetCommentInfo {
  id: string;
  body: string;
  author: SnippetOwnerInfo;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface SnippetRevisionInfo {
  id: string;
  summary: string | null;
  author: SnippetOwnerInfo;
  createdAt: string | null;
}

export interface SnippetRevisionDetail {
  id: string;
  title: string;
  description: string | null;
  summary: string | null;
  author: SnippetOwnerInfo;
  files: SnippetFileInfo[];
  createdAt: string | null;
}

export interface SnippetPage {
  content: SnippetInfo[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SnippetCommentPage {
  content: SnippetCommentInfo[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SnippetRevisionPage {
  content: SnippetRevisionInfo[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
