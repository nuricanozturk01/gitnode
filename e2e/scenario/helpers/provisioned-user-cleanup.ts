import type { APIRequestContext } from '@playwright/test';

import { deleteSelfUser } from './admin-api';

export interface ProvisionedUserTracker {
  provisionedUserTokensByUsername: Map<string, string>;
}

export function createProvisionedUserTracker(): ProvisionedUserTracker {
  return { provisionedUserTokensByUsername: new Map() };
}

export function trackProvisionedUser(
  tracker: ProvisionedUserTracker,
  login: { username: string; token: string },
): void {
  tracker.provisionedUserTokensByUsername.set(login.username, login.token);
}

export async function deleteTrackedProvisionedUsers(
  request: APIRequestContext,
  tracker: ProvisionedUserTracker,
): Promise<void> {
  for (const token of tracker.provisionedUserTokensByUsername.values()) {
    await deleteSelfUser(request, `Bearer ${token}`);
  }
}
