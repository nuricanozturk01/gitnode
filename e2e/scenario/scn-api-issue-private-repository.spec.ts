/**
 * Private repository issue API — owner CRUD and intruder access control.
 * SCN-API-ISSUE-01 — see RAPOR-SCENARIO.md
 */
import { repoIssuesApi } from '@helpers/paths';

import { expect, test } from './fixtures/scenario';
import { createScenarioIssue } from './helpers/issue-api';

test.describe('SCN-API-ISSUE — private repo issues', () => {
  // SCN-API-ISSUE-01 — Owner creates, reads, updates, deletes issue; guest cannot read private issue.
  test('owner manages issue lifecycle and guest is denied read access', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const created = await createScenarioIssue(
      bareApi,
      owner,
      owner.username,
      privateRepo.name,
      'Scenario private issue',
    );

    const listResponse = await bareApi.get(repoIssuesApi(owner.username, privateRepo.name), {
      headers: { Authorization: owner.authorization },
      params: { status: 'OPEN', page: '0', size: '25' },
    });
    expect(listResponse.status()).toBe(200);
    const listBody = await listResponse.json();
    expect(listBody.content.some((i: { number: number }) => i.number === created.number)).toBe(
      true,
    );

    const getResponse = await bareApi.get(
      `${repoIssuesApi(owner.username, privateRepo.name)}/${created.number}`,
      { headers: { Authorization: owner.authorization } },
    );
    expect(getResponse.status()).toBe(200);

    const patchResponse = await bareApi.patch(
      `${repoIssuesApi(owner.username, privateRepo.name)}/${created.number}`,
      {
        headers: { Authorization: owner.authorization },
        data: { title: 'Scenario private issue updated', status: 'OPEN' },
      },
    );
    expect(patchResponse.status()).toBe(200);
    expect((await patchResponse.json()).title).toBe('Scenario private issue updated');

    const guestGet = await bareApi.get(
      `${repoIssuesApi(owner.username, privateRepo.name)}/${created.number}`,
    );
    expect(guestGet.status()).toBe(403);

    const deleteResponse = await bareApi.delete(
      `${repoIssuesApi(owner.username, privateRepo.name)}/${created.number}`,
      { headers: { Authorization: owner.authorization } },
    );
    expect(deleteResponse.status()).toBe(204);
  });
});
