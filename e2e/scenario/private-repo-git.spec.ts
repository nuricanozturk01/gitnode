import { test, expect } from './fixtures/scenario';
import {
  gitClone,
  gitCommitAll,
  gitPush,
  httpGitRemoteUrl,
  removeWorkDir,
  scenarioWorkDir,
  writeFileAndPush,
} from './helpers/git';
import { E2E_PASSWORD } from '@helpers/test-user';

test.describe('Private repo — HTTP Git', () => {
  test('owner can clone and push with valid credentials', async ({ owner, privateRepo }, testInfo) => {
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

  test('intruder cannot push to owner private repo', async (
    { owner, intruder, privateRepo },
    testInfo,
  ) => {
    const ownerDir = scenarioWorkDir(`${testInfo.testId}-owner`);
    const ownerRemote = httpGitRemoteUrl(owner.username, privateRepo.name, {
      username: owner.username,
      password: E2E_PASSWORD,
    });
    gitClone(ownerRemote, ownerDir);

    const intruderDir = scenarioWorkDir(`${testInfo.testId}-intruder`);
    const intruderRemote = httpGitRemoteUrl(owner.username, privateRepo.name, {
      username: intruder.username,
      password: E2E_PASSWORD,
    });

    let cloneFailed = false;
    try {
      gitClone(intruderRemote, intruderDir);
    } catch {
      cloneFailed = true;
    }

    if (!cloneFailed) {
      const file = `${intruderDir}/intruder.txt`;
      const fs = await import('node:fs');
      fs.writeFileSync(file, 'intruder\n', 'utf8');
      gitCommitAll(intruderDir, 'intruder push attempt');
      let pushFailed = false;
      try {
        gitPush(intruderDir);
      } catch {
        pushFailed = true;
      }
      expect(pushFailed).toBe(true);
    } else {
      expect(cloneFailed).toBe(true);
    }

    removeWorkDir(ownerDir);
    removeWorkDir(intruderDir);
  });
});
