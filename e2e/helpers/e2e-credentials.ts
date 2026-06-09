import type { LoginInfo } from '@helpers/types';
import type { APIRequestContext } from '@playwright/test';

export interface ResolvedE2eUser {
  login: LoginInfo;
  email: string;
  password: string;
  fromEnv: boolean;
}

function envPair(
  usernameKey: string,
  passwordKey: string,
): { username: string; password: string } | null {
  const username = process.env[usernameKey]?.trim();
  const password = process.env[passwordKey];
  if (!username && !password) {
    return null;
  }
  if (!username || !password) {
    throw new Error(
      `E2E: set both ${usernameKey} and ${passwordKey}, or leave both unset for auto-registration.`,
    );
  }
  return { username, password };
}

export function ownerEnvCredentials(): { username: string; password: string } | null {
  return envPair('E2E_OWNER_USERNAME', 'E2E_OWNER_PASSWORD');
}

export function intruderEnvCredentials(): { username: string; password: string } | null {
  return envPair('E2E_INTRUDER_USERNAME', 'E2E_INTRUDER_PASSWORD');
}

export function shouldPreserveUsers(): boolean {
  if (process.env.E2E_PRESERVE_USERS === '1') {
    return true;
  }
  return ownerEnvCredentials() !== null || intruderEnvCredentials() !== null;
}

export async function loginUser(
  request: APIRequestContext,
  usernameOrEmail: string,
  password: string,
): Promise<LoginInfo> {
  const response = await request.post('/api/auth/login', {
    data: { usernameOrEmail, password },
  });
  if (!response.ok()) {
    throw new Error(`E2E login failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as LoginInfo;
}

export async function resolveOwner(
  request: APIRequestContext,
  register: (username: string) => Promise<ResolvedE2eUser>,
  generatedUsername: string,
): Promise<ResolvedE2eUser> {
  const creds = ownerEnvCredentials();
  if (!creds) {
    return register(generatedUsername);
  }
  const login = await loginUser(request, creds.username, creds.password);
  return {
    login,
    email: login.email,
    password: creds.password,
    fromEnv: true,
  };
}

export async function resolveIntruder(
  request: APIRequestContext,
  register: (username: string) => Promise<ResolvedE2eUser>,
  generatedUsername: string,
): Promise<ResolvedE2eUser> {
  const creds = intruderEnvCredentials();
  if (!creds) {
    return register(generatedUsername);
  }
  const login = await loginUser(request, creds.username, creds.password);
  return {
    login,
    email: login.email,
    password: creds.password,
    fromEnv: true,
  };
}
