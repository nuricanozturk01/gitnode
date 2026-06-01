export async function waitUntil(
  predicate: () => Promise<boolean>,
  timeoutMs = 15_000,
  intervalMs = 400,
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
