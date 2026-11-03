import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { E2E_PASSWORD } from '@helpers/test-user';

import { expect, test } from './fixtures/scenario';
import {
  gitCheckout,
  gitClone,
  httpGitRemoteUrl,
  removeWorkDir,
  scenarioWorkDir,
  writeFileAndPush,
} from './helpers/git';
import { prepareFeatureBranch } from './helpers/scenario-api';

test('protected branch blocks API, HTTP and SSH writes', async ({
  bareApi,
  owner,
  publicRepo,
}, testInfo) => {
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'protected',
  );
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  const headers = { Authorization: owner.authorization };
  const rule = await bareApi.put(`${base}/branch-protections`, {
    headers,
    data: { branchName: 'protected', requiredApprovals: 0 },
  });
  expect(rule.status()).toBe(200);
  const listed = (await (await bareApi.get(`${base}/branches`, { headers })).json()) as {
    name: string;
    isProtected: boolean;
  }[];
  expect(listed.find((b) => b.name === 'protected')?.isProtected).toBe(true);
  expect(listed.find((b) => b.name === 'main')?.isProtected).toBe(false);
  expect(
    (
      await bareApi.put(`${base}/blob/protected/README.md`, {
        headers,
        data: { content: 'blocked', commitMessage: 'edit' },
      })
    ).status(),
  ).toBe(400);
  expect((await bareApi.delete(`${base}/branches/protected`, { headers })).status()).toBe(400);
  const workDir = scenarioWorkDir(`${testInfo.testId}-protected`);
  try {
    gitClone(
      httpGitRemoteUrl(owner.username, publicRepo.name, {
        username: owner.username,
        password: E2E_PASSWORD,
      }),
      workDir,
    );
    gitCheckout(workDir, 'protected');
    expect(() =>
      writeFileAndPush(workDir, 'blocked.txt', 'blocked', 'HTTP push', 'protected'),
    ).toThrow();
  } finally {
    removeWorkDir(workDir);
  }
  const sshDir = mkdtempSync(join(tmpdir(), 'gitnode-protection-ssh-'));
  try {
    const key = join(sshDir, 'id_ed25519');
    execFileSync('ssh-keygen', ['-q', '-t', 'ed25519', '-N', '', '-f', key]);
    expect(
      (
        await bareApi.post('/api/user/ssh-keys', {
          headers,
          data: { title: 'Protection test', publicKey: readFileSync(`${key}.pub`, 'utf8') },
        })
      ).status(),
    ).toBe(201);
    const env = {
      ...process.env,
      GIT_SSH_COMMAND: `ssh -i ${key} -p 12222 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null`,
    };
    const checkout = join(sshDir, 'checkout');
    execFileSync(
      'git',
      ['clone', `ssh://git@127.0.0.1:12222/${owner.username}/${publicRepo.name}.git`, checkout],
      { env, stdio: 'pipe' },
    );
    execFileSync('git', ['-C', checkout, 'checkout', 'protected'], { env, stdio: 'pipe' });
    execFileSync('git', ['-C', checkout, 'config', 'user.name', 'Test'], { env });
    execFileSync('git', ['-C', checkout, 'config', 'user.email', 'test@example.org'], { env });
    writeFileSync(join(checkout, 'blocked.txt'), 'blocked');
    execFileSync('git', ['-C', checkout, 'add', 'blocked.txt'], { env });
    execFileSync('git', ['-C', checkout, 'commit', '-m', 'SSH push'], { env, stdio: 'pipe' });
    expect(() =>
      execFileSync('git', ['-C', checkout, 'push', 'origin', 'protected'], { env, stdio: 'pipe' }),
    ).toThrow();
  } finally {
    rmSync(sshDir, { recursive: true, force: true });
  }
});
