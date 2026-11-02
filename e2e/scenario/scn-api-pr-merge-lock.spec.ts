import { closeSync, openSync, unlinkSync } from 'node:fs';
import { join } from 'node:path';

import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

for (const strategy of ['MERGE_COMMIT', 'SQUASH', 'REBASE']) {
  test(`locked target does not falsely report ${strategy} merge success`, async ({ bareApi, owner, publicRepo }) => {
    const root = process.env.GITNODE_TEST_REPO_ROOT;
    test.skip(!root, 'Requires isolated local Git storage');
    const branch = `feature-${strategy.toLowerCase()}`;
    await prepareFeatureBranch(bareApi, owner.authorization, owner.username, publicRepo.name, branch);
    const pr = await createPullRequest(bareApi, owner.authorization, owner.username, publicRepo.name, { title: strategy, sourceBranch: branch });
    const path = `/api/repos/${owner.username}/${publicRepo.name}/pulls/${pr.number}`;
    const headers = { Authorization: owner.authorization };
    expect((await bareApi.put(`${path}/reviewers`, { headers, data: { usernames: [owner.username] } })).status()).toBe(200);
    const lock = join(root!, owner.username, `${publicRepo.name}.git`, 'refs', 'heads', 'main.lock');
    const fd = openSync(lock, 'wx');
    closeSync(fd);
    try {
      const response = await bareApi.post(`${path}/merge`, { headers, data: { strategy } });
      expect(response.status()).toBe(400);
      const detail = await bareApi.get(path, { headers });
      expect((await detail.json()).status).toBe('OPEN');
    } finally {
      unlinkSync(lock);
    }
  });
}
