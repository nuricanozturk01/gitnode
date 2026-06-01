import fs from 'fs';
import path from 'path';

import type { E2eSession } from './types';

export const SESSION_FILE = path.join(__dirname, '../../.auth/session.json');

export function saveSession(session: E2eSession): void {
  fs.mkdirSync(path.dirname(SESSION_FILE), { recursive: true });
  fs.writeFileSync(SESSION_FILE, JSON.stringify(session, null, 2), 'utf8');
}

export function loadSession(): E2eSession {
  if (!fs.existsSync(SESSION_FILE)) {
    throw new Error(
      `E2E session not found at ${SESSION_FILE}. Run global setup (playwright test --project=api).`,
    );
  }
  return JSON.parse(fs.readFileSync(SESSION_FILE, 'utf8')) as E2eSession;
}

export function patchSession(patch: Partial<E2eSession>): E2eSession {
  const session = { ...loadSession(), ...patch };
  saveSession(session);
  return session;
}
