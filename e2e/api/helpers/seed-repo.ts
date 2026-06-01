import type { APIRequestContext } from '@playwright/test';

import { repoBranchesApi, reposApi } from './paths';

async function waitForMainBranch(
  request: APIRequestContext,
  owner: string,
  repo: string,
  authorization: string,
): Promise<void> {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const response = await request.get(`${repoBranchesApi(owner, repo)}/main`, {
      headers: { Authorization: authorization },
    });
    if (response.ok()) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Timed out waiting for branch main on ${owner}/${repo}`);
}

/** Adds README.md on {@code main} so tree/commit/tag endpoints have content. */
export async function seedReadmeOnMain(
  request: APIRequestContext,
  owner: string,
  repo: string,
  authorization: string,
): Promise<void> {
  await waitForMainBranch(request, owner, repo, authorization);

  const response = await request.put(`${reposApi(owner, repo)}/blob/main/README.md`, {
    headers: { Authorization: authorization },
    data: {
      content: '# E2E Fixture\n\nSeeded for API tests.\n',
      commitMessage: 'e2e: seed README',
    },
  });
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`seed README failed (${response.status()}): ${body}`);
  }
}
