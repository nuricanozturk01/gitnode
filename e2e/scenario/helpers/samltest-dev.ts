import { SAMLTEST_DEV_API } from './env';

export interface SamlTestUser {
  email: string;
  firstName: string;
  lastName: string;
}

export interface SamlTestApp {
  id: string;
  users: SamlTestUser[];
  spAcsUrl?: string;
  spEntityId?: string;
}

export interface CreateSamlTestAppOptions {
  spAcsUrl: string;
  spEntityId?: string;
  users: SamlTestUser[];
}

export async function createSamlTestApp(options: CreateSamlTestAppOptions): Promise<SamlTestApp> {
  const response = await fetch(SAMLTEST_DEV_API, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      spAcsUrl: options.spAcsUrl,
      spEntityId: options.spEntityId ?? 'originhub',
      users: options.users,
    }),
  });

  if (!response.ok) {
    throw new Error(`samltest.dev create app failed (${response.status}): ${await response.text()}`);
  }

  return (await response.json()) as SamlTestApp;
}

export function samlTestMetadataUri(appId: string): string {
  return `https://www.samltest.dev/apps/${appId}/metadata`;
}

export async function deleteSamlTestApp(appId: string): Promise<void> {
  const response = await fetch(`${SAMLTEST_DEV_API}/${appId}`, { method: 'DELETE' });

  if (response.status !== 204 && response.status !== 404) {
    console.warn(`samltest.dev delete app failed (${response.status}): ${await response.text()}`);
  }
}
