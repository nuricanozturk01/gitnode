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

export interface ContributionBreakdown {
  issues: number;
  issueComments: number;
  pullRequests: number;
  pullRequestComments: number;
  pullRequestMerges: number;
  releases: number;
  snippets: number;
  snippetRevisions: number;
  snippetComments: number;
}

export interface ContributionDay {
  date: string;
  count: number;
  level: 0 | 1 | 2 | 3 | 4;
  breakdown: ContributionBreakdown;
}

export interface UserContributionsResponse {
  username: string;
  rangeStart: string;
  rangeEnd: string;
  totalContributions: number;
  longestStreak: number;
  currentStreak: number;
  includesPrivateActivity: boolean;
  days: ContributionDay[];
}
