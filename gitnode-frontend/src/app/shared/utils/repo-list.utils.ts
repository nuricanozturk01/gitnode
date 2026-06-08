///
/// Copyright 2026 the original author or authors.
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
///

import type { RepoInfo } from '../../domain/repository/models/repo-info.model';

export type RepoListSort = 'updated' | 'name' | 'created';

function ownerLogin(repo: RepoInfo): string {
  return repo.owner?.username?.trim() ?? '';
}

function instantMs(iso: string | null | undefined): number {
  if (iso == null || iso === '') return 0;
  const t = new Date(iso).getTime();
  return Number.isFinite(t) ? t : 0;
}

/** Backend may omit topics or send null (e.g. empty DB column). */
export function normalizeRepoTopics(repo: RepoInfo): string[] {
  const raw = repo.topics as unknown;
  if (raw == null) return [];
  if (Array.isArray(raw)) return raw as string[];
  return [];
}

export function collectTopicsLower(repos: readonly RepoInfo[]): string[] {
  const s = new Set<string>();
  for (const r of repos) {
    for (const t of normalizeRepoTopics(r)) {
      s.add(t.toLowerCase());
    }
  }
  return [...s].sort((a, b) => a.localeCompare(b));
}

export function matchesRepoSearch(repo: RepoInfo, queryLower: string): boolean {
  if (!queryLower) return true;
  return (
    (repo.name ?? '').toLowerCase().includes(queryLower) ||
    (repo.description ?? '').toLowerCase().includes(queryLower) ||
    ownerLogin(repo).toLowerCase().includes(queryLower) ||
    normalizeRepoTopics(repo).some((t) => t.toLowerCase().includes(queryLower))
  );
}

/** OR semantics: repo must include at least one selected topic (case-insensitive). */
export function matchesTopicFilter(repo: RepoInfo, selectedTopicsLower: readonly string[]): boolean {
  if (selectedTopicsLower.length === 0) return true;
  const set = new Set(selectedTopicsLower);
  return normalizeRepoTopics(repo).some((t) => set.has(t.toLowerCase()));
}

export function compareReposBySort(a: RepoInfo, b: RepoInfo, sort: RepoListSort): number {
  switch (sort) {
    case 'name': {
      const fa = `${ownerLogin(a)}/${a.name ?? ''}`;
      const fb = `${ownerLogin(b)}/${b.name ?? ''}`;
      return fa.localeCompare(fb, undefined, { sensitivity: 'base' });
    }
    case 'created':
      return instantMs(b.createdAt) - instantMs(a.createdAt);
    default:
      return instantMs(b.updatedAt) - instantMs(a.updatedAt);
  }
}
