import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

import { getApiBaseUrl } from './env';

const WORK_ROOT = path.join(__dirname, '..', '.workdirs');

export function httpGitRemoteUrl(
  owner: string,
  repo: string,
  credentials?: { username: string; password: string },
): string {
  const base = getApiBaseUrl().replace(/\/$/, '');
  const pathPart = `/git/${owner}/${repo}.git`;
  if (!credentials) {
    return `${base}${pathPart}`;
  }
  const { username, password } = credentials;
  const encodedUser = encodeURIComponent(username);
  const encodedPass = encodeURIComponent(password);
  const url = new URL(pathPart, base);
  url.username = encodedUser;
  url.password = encodedPass;
  return url.toString();
}

export function scenarioWorkDir(testId: string): string {
  const safe = testId.replace(/[^a-zA-Z0-9_-]/g, '_');
  return path.join(WORK_ROOT, safe);
}

export function removeWorkDir(dir: string): void {
  fs.rmSync(dir, { recursive: true, force: true });
}

/** Removes all scenario Git working directories (see global-teardown). */
export function removeAllWorkDirs(): void {
  fs.rmSync(WORK_ROOT, { recursive: true, force: true });
}

/** Disables OS/keychain credential helpers so auth comes only from the remote URL. */
const GIT_NO_CREDENTIALS = ['-c', 'credential.helper='];

export function runGit(
  args: string[],
  options?: { cwd?: string; expectFailure?: boolean },
): string {
  const cwd = options?.cwd;
  try {
    return execFileSync('git', [...GIT_NO_CREDENTIALS, ...args], {
      cwd,
      encoding: 'utf8',
      stdio: ['pipe', 'pipe', 'pipe'],
      env: {
        ...process.env,
        GIT_TERMINAL_PROMPT: '0',
      },
    });
  } catch (error: unknown) {
    if (options?.expectFailure && error && typeof error === 'object' && 'stderr' in error) {
      const err = error as { stderr?: Buffer | string; stdout?: Buffer | string };
      const stderr = err.stderr?.toString() ?? '';
      const stdout = err.stdout?.toString() ?? '';
      return `${stdout}\n${stderr}`;
    }
    throw error;
  }
}

export function gitCheckout(repoDir: string, branch: string): void {
  runGit(['-C', repoDir, 'checkout', branch], {});
}

export function gitClone(remote: string, targetDir: string, expectFailure = false): void {
  fs.mkdirSync(path.dirname(targetDir), { recursive: true });
  removeWorkDir(targetDir);
  runGit(['clone', remote, targetDir], { expectFailure });
}

export function gitCommitAll(repoDir: string, message: string): void {
  runGit(['-C', repoDir, 'config', 'user.email', 'e2e@gitnode.test'], {});
  runGit(['-C', repoDir, 'config', 'user.name', 'E2E Scenario'], {});
  runGit(['-C', repoDir, 'add', '-A'], {});
  runGit(['-C', repoDir, 'commit', '-m', message], {});
}

export function gitPush(repoDir: string, branch = 'main', expectFailure = false): string {
  return runGit(['-C', repoDir, 'push', 'origin', branch], { expectFailure });
}

export function writeFileAndPush(
  repoDir: string,
  relativePath: string,
  content: string,
  message: string,
  branch = 'main',
): void {
  const full = path.join(repoDir, relativePath);
  fs.mkdirSync(path.dirname(full), { recursive: true });
  fs.writeFileSync(full, content, 'utf8');
  gitCommitAll(repoDir, message);
  gitPush(repoDir, branch);
}
