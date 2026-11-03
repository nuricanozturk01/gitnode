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
import { createPullRequest, prepareFeatureBranch, putBlob } from './helpers/scenario-api';
import { SSH_ENABLED, SSH_HOST, SSH_PORT } from './helpers/ssh';

test('pull request workflow is tied to the commit that triggered it', async ({
  bareApi,
  owner,
  publicRepo,
}, testInfo) => {
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  const headers = { Authorization: owner.authorization };
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'ci-feature',
  );
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'ci-feature',
    '.gitnode/workflows/ci.yaml',
    'name: SHA check\non:\n  pull_request:\n    branches: [main]\njobs:\n  build:\n    runs-on: self-hosted\n    steps:\n      - run: echo test\n',
    'Add CI',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'CI', sourceBranch: 'ci-feature' },
  );
  const prResponse = await bareApi.get(`${base}/pulls/${pr.number}`, { headers });
  const { sourceSha } = (await prResponse.json()) as { sourceSha: string };
  await expect
    .poll(
      async () => {
        const runs = await bareApi.get(`${base}/actions/runs`, {
          headers,
          params: { triggerEvent: 'pull_request', triggerRef: 'refs/heads/ci-feature' },
        });
        if (!runs.ok()) return '';
        return (
          ((await runs.json()).content[0] as { triggerSha?: string } | undefined)?.triggerSha ?? ''
        );
      },
      { timeout: 15000 },
    )
    .toBe(sourceSha);
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'ci-feature',
    'new-change.txt',
    'second commit',
    'Advance PR',
  );
  await expect
    .poll(
      async () => {
        const runs = await bareApi.get(`${base}/actions/runs`, {
          headers,
          params: { triggerEvent: 'pull_request', triggerRef: 'refs/heads/ci-feature' },
        });
        if (!runs.ok()) return false;
        const content = (await runs.json()).content as { triggerSha?: string }[];
        return (
          content.length >= 2 && !!content[0].triggerSha && content[0].triggerSha !== sourceSha
        );
      },
      { timeout: 15000 },
    )
    .toBe(true);
  const workDir = scenarioWorkDir(`${testInfo.testId}-ci-push`);
  try {
    const remote = httpGitRemoteUrl(owner.username, publicRepo.name, {
      username: owner.username,
      password: E2E_PASSWORD,
    });
    gitClone(remote, workDir);
    gitCheckout(workDir, 'ci-feature');
    writeFileAndPush(workDir, 'git-push.txt', 'third commit', 'Git push', 'ci-feature');
  } finally {
    removeWorkDir(workDir);
  }
  await expect
    .poll(
      async () => {
        const runs = await bareApi.get(`${base}/actions/runs`, {
          headers,
          params: { triggerEvent: 'pull_request', triggerRef: 'refs/heads/ci-feature' },
        });
        if (!runs.ok()) return 0;
        return ((await runs.json()).content as unknown[]).length;
      },
      { timeout: 15000 },
    )
    .toBeGreaterThanOrEqual(3);
  if (!SSH_ENABLED) {
    return;
  }
  const sshDir = mkdtempSync(join(tmpdir(), 'gitnode-ci-ssh-'));
  try {
    const key = join(sshDir, 'id_ed25519');
    execFileSync('ssh-keygen', ['-q', '-t', 'ed25519', '-N', '', '-f', key]);
    const added = await bareApi.post('/api/user/ssh-keys', {
      headers,
      data: { title: `CI SSH test ${Date.now()}`, publicKey: readFileSync(`${key}.pub`, 'utf8') },
    });
    expect(added.status()).toBe(201);
    const env = {
      ...process.env,
      GIT_SSH_COMMAND: `ssh -i ${key} -p ${SSH_PORT} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null`,
    };
    const checkout = join(sshDir, 'checkout');
    execFileSync(
      'git',
      [
        'clone',
        `ssh://git@${SSH_HOST}:${SSH_PORT}/${owner.username}/${publicRepo.name}.git`,
        checkout,
      ],
      { env, stdio: 'pipe' },
    );
    execFileSync('git', ['-C', checkout, 'checkout', 'ci-feature'], { env, stdio: 'pipe' });
    execFileSync('git', ['-C', checkout, 'config', 'user.name', 'CI Test'], { env });
    execFileSync('git', ['-C', checkout, 'config', 'user.email', 'ci@example.org'], { env });
    writeFileSync(join(checkout, 'ssh-push.txt'), 'fourth commit');
    execFileSync('git', ['-C', checkout, 'add', 'ssh-push.txt'], { env });
    execFileSync('git', ['-C', checkout, 'commit', '-m', 'SSH push'], { env, stdio: 'pipe' });
    execFileSync('git', ['-C', checkout, 'push', 'origin', 'ci-feature'], { env, stdio: 'pipe' });
    await expect
      .poll(
        async () => {
          const runs = await bareApi.get(`${base}/actions/runs`, {
            headers,
            params: { triggerEvent: 'pull_request', triggerRef: 'refs/heads/ci-feature' },
          });
          if (!runs.ok()) return 0;
          return ((await runs.json()).content as unknown[]).length;
        },
        { timeout: 15000 },
      )
      .toBeGreaterThanOrEqual(4);
  } finally {
    rmSync(sshDir, { recursive: true, force: true });
  }
});
