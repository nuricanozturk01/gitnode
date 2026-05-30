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

import type { Repo } from '../models/repo.model';
import type { Branch } from '../models/branch.model';
import type { Commit } from '../models/commit.model';
import type { TreeEntry } from '../models/tree-entry.model';
import type { Blob } from '../models/blob.model';

export interface RepoPort {
  getRepo(owner: string, repo: string): Promise<Repo>;
  getRepos(owner: string): Promise<Repo[]>;
  createRepo(data: Partial<Repo>): Promise<Repo>;
  getBranches(owner: string, repo: string): Promise<Branch[]>;
  getCommits(owner: string, repo: string, branch: string, page?: number): Promise<Commit[]>;
  getCommit(owner: string, repo: string, sha: string): Promise<Commit>;
  getTree(owner: string, repo: string, branch: string, path?: string): Promise<TreeEntry[]>;
  getBlob(owner: string, repo: string, branch: string, path: string): Promise<Blob>;
}
