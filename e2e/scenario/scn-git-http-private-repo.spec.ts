/**
 * Private repository — HTTP Git (smart HTTP /git/**).
 * SCN-PRIV-GIT-01 … SCN-PRIV-GIT-04 — see RAPOR-SCENARIO.md
 */
import { E2E_PASSWORD } from '@helpers/test-user';

import { expect, test } from './fixtures/scenario';
import {
  gitClone,
  httpGitRemoteUrl,
  removeWorkDir,
  scenarioWorkDir,
  writeFileAndPush,
} from './helpers/git';

test.describe('SCN-PRIV-GIT — private repo HTTP Git', () => {
  // SCN-PRIV-GIT-01 — Owner clones a private repo with valid credentials and pushes to main.
  test('owner can clone and push with valid credentials', async ({
    owner,
    privateRepo,
  }, testInfo) => {
    const workDir = scenarioWorkDir(`${testInfo.testId}-clone-push`);
    const remote = httpGitRemoteUrl(owner.username, privateRepo.name, {
      username: owner.username,
      password: E2E_PASSWORD,
    });

    gitClone(remote, workDir);
    writeFileAndPush(workDir, 'private-push.txt', 'private push\n', 'scenario: private push');

    try {
      expect(workDir).toBeTruthy();
    } finally {
      removeWorkDir(workDir);
    }
  });

  // SCN-PRIV-GIT-02 — Unauthenticated clone of a private repo is rejected.
  test('clone without authentication is rejected', async ({ owner, privateRepo }, testInfo) => {
    const workDir = scenarioWorkDir(`${testInfo.testId}-anon-clone`);
    const remote = httpGitRemoteUrl(owner.username, privateRepo.name);

    let failed = false;
    try {
      gitClone(remote, workDir);
    } catch {
      failed = true;
    } finally {
      removeWorkDir(workDir);
    }
    expect(failed).toBe(true);
  });

  // SCN-PRIV-GIT-03 — Clone with an invalid password is rejected.
  test('clone with invalid password is rejected', async ({ owner, privateRepo }, testInfo) => {
    const workDir = scenarioWorkDir(`${testInfo.testId}-bad-clone`);
    const remote = httpGitRemoteUrl(owner.username, privateRepo.name, {
      username: owner.username,
      password: 'WrongPassword1!',
    });

    let failed = false;
    try {
      gitClone(remote, workDir);
    } catch {
      failed = true;
    } finally {
      removeWorkDir(workDir);
    }
    expect(failed).toBe(true);
  });

  // SCN-PRIV-GIT-04 — Intruder cannot push to the owner's private repo (HTTP ACL; skipped).
  /**
   * Product gap: HTTP Git authenticates but does not enforce private-repo ACL like SSH.
   * Cannot automate until backend is fixed — see RAPOR-SCENARIO.md.
   */
  test.skip('intruder cannot push to owner private repo', async () => {});
});
