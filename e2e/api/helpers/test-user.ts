/** Password satisfies backend RegistrationForm / LoginForm rules. */
export const E2E_PASSWORD = 'OriginHub1!';

export function uniqueUsername(prefix = 'e2e'): string {
  const suffix = `${Date.now()}`.slice(-8);
  return `${prefix}_${suffix}`.slice(0, 25);
}

export function uniqueEmail(username: string): string {
  return `${username}@e2e.originhub.test`;
}
