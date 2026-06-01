/** Modulith JDBC events can lag on CI / remote API; allow extra time there. */
export function getEventWaitTimeoutMs(): number {
  return process.env.CI ? 60_000 : 25_000;
}

export async function waitUntil(
  predicate: () => Promise<boolean>,
  timeoutMs = getEventWaitTimeoutMs(),
  intervalMs = 500,
): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await predicate()) {
      return;
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  throw new Error('waitUntil timed out');
}
