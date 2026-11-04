import type { APIRequestContext } from '@playwright/test';

const PAGE_SIZE = 50;

interface RepoListPage {
  content: { name: string }[];
  totalPages: number;
}

export async function listAllRepoNames(
  api: APIRequestContext,
  ownerUsername: string,
  headers?: Record<string, string>,
): Promise<string[]> {
  const names: string[] = [];
  for (let page = 0, totalPages = 1; page < totalPages; page += 1) {
    const response = await api.get(`/api/repo/${ownerUsername}`, {
      headers,
      params: { page: String(page), size: String(PAGE_SIZE) },
    });
    if (response.status() !== 200) {
      throw new Error(`repo listing page ${page} returned ${response.status()}`);
    }
    const body = (await response.json()) as RepoListPage;
    names.push(...body.content.map((r) => r.name));
    totalPages = body.totalPages;
  }
  return names;
}
