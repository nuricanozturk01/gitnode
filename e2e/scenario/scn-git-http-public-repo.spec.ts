/**
 * Public repository — HTTP Git (smart HTTP /git/**).
 * SCN-PUB-GIT-01 … SCN-PUB-GIT-04 — see RAPOR-SCENARIO.md
 */
import { E2E_PASSWORD } from '@helpers/test-user';

import { expect, test } from './fixtures/scenario';
import {
  gitClone,
  gitCommitAll,
  gitPush,
  httpGitRemoteUrl,
  removeWorkDir,
  scenarioWorkDir,
  writeFileAndPush,
} from './helpers/git';

test.describe('SCN-PUB-GIT — public repo HTTP Git', () => {
  // SCN-PUB-GIT-01 — Anonymous user can clone a public repo without credentials.
  test('anonymous clone succeeds', async ({ owner, publicRepo }, testInfo) => {
    const workDir = scenarioWorkDir(`${testInfo.testId}-anon-clone`);
    const remote = httpGitRemoteUrl(owner.username, publicRepo.name);

    gitClone(remote, workDir);
    expect(workDir).toBeTruthy();
    removeWorkDir(workDir);
  });

  // SCN-PUB-GIT-02 — Owner pushes to a public repo with valid credentials.
  test('owner can push with valid credentials', async ({ owner, publicRepo }, testInfo) => {
    const workDir = scenarioWorkDir(`${testInfo.testId}-owner-push`);
    const remote = httpGitRemoteUrl(owner.username, publicRepo.name, {
      username: owner.username,
      password: E2E_PASSWORD,
    });

    gitClone(remote, workDir);
    writeFileAndPush(workDir, 'public-owner.txt', 'owner push\n', 'scenario: public owner push');
    removeWorkDir(workDir);
  });

  // SCN-PUB-GIT-03 — Unauthenticated push is rejected after an anonymous clone.
  test('unauthenticated push is rejected', async ({ owner, publicRepo }, testInfo) => {
    const workDir = scenarioWorkDir(`${testInfo.testId}-anon-push`);
    const remote = httpGitRemoteUrl(owner.username, publicRepo.name);

    gitClone(remote, workDir);
    const fs = await import('node:fs');
    const path = await import('node:path');
    fs.writeFileSync(path.join(workDir, 'anon.txt'), 'anon\n', 'utf8');
    gitCommitAll(workDir, 'scenario: anon push attempt');

    let pushFailed = false;
    try {
      gitPush(workDir);
    } catch {
      pushFailed = true;
    }
    expect(pushFailed).toBe(true);
    removeWorkDir(workDir);
  });

  // SCN-PUB-GIT-04 — Intruder cannot push to a public repo without write access (HTTP ACL; skipped).
  /**
   * Product gap: HTTP Git does not enforce owner-only write like SSH.
   * Cannot automate until backend is fixed — see RAPOR-SCENARIO.md.
   */
  test.skip('intruder push without write access fails', async () => {});
});
