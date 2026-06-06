import { randomBytes } from 'node:crypto';

/** Unique id per test run — slug/email suffixes stay collision-free on re-runs. */
export function createE2eRunId(): string {
  const random = randomBytes(4).toString('hex');
  return `${Date.now().toString(36)}${random}`;
}
