import fs from 'node:fs';
import path from 'node:path';

import { E2E_PASSWORD } from '@helpers/test-user';

import { expect, test } from './fixtures/scenario';
import { gitClone, httpGitRemoteUrl, removeWorkDir, runGit, scenarioWorkDir } from './helpers/git';
import { createScenarioRepo } from './helpers/scenario-api';
import type { ScenarioUser } from './helpers/types';

interface Contributions {
  rangeEnd: string;
  days: { breakdown: { commits: number } }[];
}

test('extra e-mail addresses attribute commits and the time zone moves the calendar', async ({
  bareApi,
  intruder,
}, testInfo) => {
  const username = `heatx_${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
  const registered = await bareApi.post('/api/auth/register', {
    data: { username, email: `${username}@example.org`, password: E2E_PASSWORD },
  });
  const login = (await registered.json()) as ScenarioUser;
  const owner: ScenarioUser = {
    ...login,
    username,
    email: `${username}@example.org`,
    password: E2E_PASSWORD,
    authorization: `Bearer ${login.token}`,
  };
  const headers = { Authorization: owner.authorization };
  const repo = await createScenarioRepo(bareApi, owner, { isPrivate: false });
  const read = async () =>
    (await (
      await bareApi.get(`/api/users/${username}/contributions`, { headers })
    ).json()) as Contributions;
  const commits = (c: Contributions) => c.days.reduce((sum, day) => sum + day.breakdown.commits, 0);
  await expect.poll(async () => commits(await read()), { timeout: 20_000 }).toBe(1);

  // E-mail validation and ownership rules.
  const emails = '/api/users/me/emails';
  const extra = `${username}.old@example.net`;
  expect((await bareApi.post(emails, { data: { email: extra } })).status()).toBe(401);
  expect(
    (await bareApi.post(emails, { headers, data: { email: 'not-an-address' } })).status(),
  ).toBe(400);
  expect((await bareApi.post(emails, { headers, data: { email: owner.email } })).status()).toBe(
    400,
  );
  expect((await bareApi.post(emails, { headers, data: { email: intruder.email } })).status()).toBe(
    400,
  );
  expect(
    (
      await bareApi.post(emails, { headers, data: { email: `x@users.noreply.example.org` } })
    ).status(),
  ).toBe(400);
  const added = await bareApi.post(emails, { headers, data: { email: extra.toUpperCase() } });
  expect(added.status()).toBe(201);
  const created = (await added.json()) as { id: string; email: string };
  expect(created.email).toBe(extra);
  expect((await bareApi.post(emails, { headers, data: { email: extra } })).status()).toBe(400);
  expect(((await (await bareApi.get(emails, { headers })).json()) as unknown[]).length).toBe(1);

  const workDir = scenarioWorkDir(`${testInfo.testId}-heat-email`);
  try {
    gitClone(httpGitRemoteUrl(username, repo.name, { username, password: E2E_PASSWORD }), workDir);
    fs.writeFileSync(path.join(workDir, 'old.txt'), 'old', 'utf8');
    runGit(['-C', workDir, 'add', '-A']);
    runGit([
      '-C',
      workDir,
      '-c',
      'user.name=Old Me',
      '-c',
      `user.email=${extra}`,
      'commit',
      '-m',
      'From an old address',
    ]);
    runGit(['-C', workDir, 'push', 'origin', 'main']);
  } finally {
    removeWorkDir(workDir);
  }
  await expect.poll(async () => commits(await read()), { timeout: 20_000 }).toBe(2);

  expect((await bareApi.delete(`${emails}/${created.id}`, { headers })).status()).toBe(204);
  expect((await bareApi.delete(`${emails}/${created.id}`, { headers })).status()).toBe(404);

  // Time zone: unknown zones are refused and a far-east zone is a day ahead of a far-west one.
  const profile = (timezone: string) =>
    bareApi.patch('/api/users/me/profile', {
      headers,
      data: { bio: '', website: '', location: '', profileReadme: '', timezone },
    });
  expect((await profile('Mars/Olympus')).status()).toBe(400);
  expect((await profile('Pacific/Kiritimati')).status()).toBe(200);
  const east = (await read()).rangeEnd;
  expect((await profile('Pacific/Pago_Pago')).status()).toBe(200);
  const west = (await read()).rangeEnd;
  expect(east > west).toBe(true);
  expect(
    ((await (await bareApi.get('/api/users/me', { headers })).json()) as { timezone: string })
      .timezone,
  ).toBe('Pacific/Pago_Pago');
  expect((await profile('')).status()).toBe(200);
  expect(
    (
      (await (await bareApi.get('/api/users/me', { headers })).json()) as {
        timezone: string | null;
      }
    ).timezone,
  ).toBeNull();
});
