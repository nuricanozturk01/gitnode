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

import type { AuthorInfo } from './author-info.model';
import type { CommitStats } from './commit-stats.model';
import type { FileDiff } from './file-diff.model';

export interface CommitDetail {
  sha: string;
  shortSha: string;
  message: string;
  description: string | null;
  author: AuthorInfo;
  committedAt: string;
  parentShas: string[];
  stats: CommitStats;
  files: FileDiff[];
}
