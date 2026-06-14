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

export type AiProvider = 'OPENAI' | 'ANTHROPIC' | 'GEMINI' | 'LOCAL';

export interface UserAiSettings {
  id: string;
  provider: AiProvider;
  hasApiKey: boolean;
  baseUrl: string | null;
  model: string | null;
  enabled: boolean;
}

export interface UpdateAiSettingsRequest {
  provider: AiProvider;
  apiKey: string | null;
  baseUrl: string | null;
  model: string | null;
  enabled: boolean;
}

export interface TestAiConnectionRequest {
  provider?: AiProvider;
  apiKey?: string | null;
  baseUrl?: string | null;
  model?: string | null;
}

export interface TestAiConnectionResponse {
  message: string;
}

export interface CommitSuggestionResponse {
  message: string;
}

export interface PrDescriptionResponse {
  title: string;
  description: string;
}

export type ReviewStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';
export type ReviewCategory = 'BUG' | 'SECURITY' | 'PERFORMANCE' | 'CODE_QUALITY' | 'GENERAL';
export type ReviewSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';

export interface AiCodeReviewComment {
  id: string;
  filePath: string;
  lineNumber: number | null;
  category: ReviewCategory;
  severity: ReviewSeverity;
  comment: string;
  suggestion: string | null;
}

export interface AiCodeReview {
  id: string;
  status: ReviewStatus;
  summary: string | null;
  statusMessage: string | null;
  createdAt: string;
  comments: {
    content: AiCodeReviewComment[];
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface CodebaseAnalysisDimension {
  key: string;
  label: string;
  score: number | null;
  reason: string | null;
  issues: string | null;
  fix: string | null;
}

export interface CodebaseAnalysis {
  id: string;
  branch: string;
  status: ReviewStatus;
  archScore: number | null;
  qualityScore: number | null;
  perfScore: number | null;
  memoryScore: number | null;
  scalabilityScore: number | null;
  securityScore: number | null;
  overallScore: number | null;
  summary: string | null;
  recommendations: string | null;
  dimensions: CodebaseAnalysisDimension[];
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
