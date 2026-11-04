/**
 * Private repository — HTTP Git (smart HTTP /git/**).
 * SCN-PRIV-GIT-01 … SCN-PRIV-GIT-04 — see RAPOR-SCENARIO.md
 */
import fs from 'node:fs';
import path from 'node:path';

import { repoApi } from '@helpers/paths';
import { E2E_PASSWORD } from '@helpers/test-user';

import { expect, test } from './fixtures/scenario';
import { adminLogin, deleteSelfUser, setUserEnabled } from './helpers/admin-api';
import { getAdminCredentials, hasAdminCredentials } from './helpers/env';
import {
  gitClone,
  gitCommitAll,
  gitPush,
  httpGitRemoteUrl,
  removeWorkDir,
  runGit,
  scenarioWorkDir,
  writeFileAndPush,
} from './helpers/git';
import { addCollaborator, createScenarioRepo } from './helpers/scenario-api';
import type { ScenarioUser } from './helpers/types';

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

    try {
      gitClone(remote, workDir);
      writeFileAndPush(workDir, 'private-push.txt', 'private push\n', 'scenario: private push');
      // If writeFileAndPush does not throw, clone + commit + push all succeeded.
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

  // SCN-PRIV-GIT-04 — Intruder with a valid password cannot clone or push the owner's private repo.
  test('intruder cannot clone or push owner private repo', async ({
    owner,
    intruder,
    privateRepo,
  }, testInfo) => {
    const intruderRemote = httpGitRemoteUrl(owner.username, privateRepo.name, {
      username: intruder.username,
      password: intruder.password,
    });
    const cloneDir = scenarioWorkDir(`${testInfo.testId}-intruder-clone`);
    const pushDir = scenarioWorkDir(`${testInfo.testId}-intruder-push`);

    try {
      let cloneFailed = false;
      try {
        gitClone(intruderRemote, cloneDir);
      } catch {
        cloneFailed = true;
      }
      expect(cloneFailed).toBe(true);

      gitClone(
        httpGitRemoteUrl(owner.username, privateRepo.name, {
          username: owner.username,
          password: E2E_PASSWORD,
        }),
        pushDir,
      );
      runGit(['-C', pushDir, 'remote', 'set-url', 'origin', intruderRemote]);
      fs.writeFileSync(path.join(pushDir, 'intruder.txt'), 'intruder\n', 'utf8');
      gitCommitAll(pushDir, 'scenario: intruder push');

      let pushFailed = false;
      try {
        gitPush(pushDir);
      } catch {
        pushFailed = true;
      }
      expect(pushFailed).toBe(true);
    } finally {
      removeWorkDir(cloneDir);
      removeWorkDir(pushDir);
    }
  });

  test('collaborator with PUSH can push over HTTP', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }, testInfo) => {
    await addCollaborator(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      intruder,
      ['READ', 'PUSH'],
    );
    const workDir = scenarioWorkDir(`${testInfo.testId}-collaborator-push`);
    const remote = httpGitRemoteUrl(owner.username, privateRepo.name, {
      username: intruder.username,
      password: intruder.password,
    });

    try {
      gitClone(remote, workDir);
      writeFileAndPush(workDir, 'collaborator-push.txt', 'collaborator push\n', 'scenario: push');
    } finally {
      removeWorkDir(workDir);
    }
  });

  // SCN-PRIV-GIT-05 — A disabled account is refused over HTTP Git until re-enabled.
  test('disabled account cannot clone until re-enabled', async ({ bareApi }, testInfo) => {
    test.skip(
      !hasAdminCredentials(),
      'Needs a platform admin: set E2E_ADMIN_USERNAME and E2E_ADMIN_PASSWORD for a deployed API',
    );
    const username = `gitdis_${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
    const register = await bareApi.post('/api/auth/register', {
      data: { username, email: `${username}@example.org`, password: E2E_PASSWORD },
    });
    expect(register.status()).toBe(200);
    const body = (await register.json()) as { token: string; refreshToken: string };
    const user: ScenarioUser = {
      username,
      email: `${username}@example.org`,
      password: E2E_PASSWORD,
      token: body.token,
      refreshToken: body.refreshToken,
      expiresIn: 0,
      refreshExpiresIn: 0,
      authorization: `Bearer ${body.token}`,
    };
    const repo = await createScenarioRepo(bareApi, user, { isPrivate: true, namePrefix: 'dis' });
    const admin = getAdminCredentials();
    const adminSession = await adminLogin(bareApi, admin.username, admin.password);
    const remote = httpGitRemoteUrl(username, repo.name, { username, password: E2E_PASSWORD });
    const tryClone = (label: string): boolean => {
      const dir = scenarioWorkDir(`${testInfo.testId}-${label}`);
      try {
        gitClone(remote, dir);
        return true;
      } catch {
        return false;
      } finally {
        removeWorkDir(dir);
      }
    };

    try {
      expect(tryClone('enabled')).toBe(true);
      await setUserEnabled(bareApi, adminSession, username, false);
      expect(tryClone('disabled')).toBe(false);
      await setUserEnabled(bareApi, adminSession, username, true);
      expect(tryClone('reenabled')).toBe(true);
    } finally {
      await setUserEnabled(bareApi, adminSession, username, true);
      await deleteSelfUser(bareApi, user.authorization);
    }
  });

  // SCN-PRIV-GIT-06 — A repository whose name contains dots (user.github.io) clones over HTTP Git.
  test('repository name with dots can be cloned', async ({ owner, bareApi }, testInfo) => {
    const name = `${owner.username}.github.io`;
    const created = await bareApi.post(repoApi, {
      headers: { Authorization: owner.authorization },
      data: { name, description: 'dotted', isPrivate: true },
    });
    expect(created.status()).toBeLessThan(300);

    const dir = scenarioWorkDir(`${testInfo.testId}-dotted`);
    try {
      gitClone(
        httpGitRemoteUrl(owner.username, name, {
          username: owner.username,
          password: E2E_PASSWORD,
        }),
        dir,
      );
    } finally {
      removeWorkDir(dir);
    }
  });
});
