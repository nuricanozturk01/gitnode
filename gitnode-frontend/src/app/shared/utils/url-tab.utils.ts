import { Location } from '@angular/common';

/** Resolve a URL hash fragment to one of the allowed tab ids. */
export function parseUrlTab<T extends string>(
  fragment: string | null | undefined,
  allowed: readonly T[],
  defaultTab: T,
): T {
  if (!fragment) return defaultTab;
  const match = allowed.find((tab) => tab === fragment);
  return match ?? defaultTab;
}

/**
 * Like {@link parseUrlTab}, but also matches compound fragments such as `actions-secrets`
 * when `actions` is an allowed tab id.
 */
export function parseUrlTabPrefix<T extends string>(
  fragment: string | null | undefined,
  allowed: readonly T[],
  defaultTab: T,
): T {
  if (!fragment) return defaultTab;
  const direct = allowed.find((tab) => tab === fragment);
  if (direct) return direct;
  const prefixed = allowed.find((tab) => fragment.startsWith(`${tab}-`));
  return prefixed ?? defaultTab;
}

/** Read the suffix from a compound fragment like `actions-secrets`. */
export function parseCompoundSubTab<T extends string>(
  fragment: string | null | undefined,
  parentTab: string,
  allowed: readonly T[],
  defaultSubTab: T,
): T {
  if (!fragment) return defaultSubTab;
  const prefix = `${parentTab}-`;
  if (fragment.startsWith(prefix)) {
    const sub = fragment.slice(prefix.length);
    const match = allowed.find((tab) => tab === sub);
    if (match) return match;
  }
  if (fragment === parentTab) return defaultSubTab;
  return defaultSubTab;
}

/** Update the URL hash without triggering navigation. */
export function replaceUrlFragment(location: Location, fragment: string | null | undefined): void {
  const path = location.path(false).split('#')[0];
  location.replaceState(fragment ? `${path}#${fragment}` : path);
}

/** Build a compound hash fragment (`actions-secrets`) or a plain tab id. */
export function buildCompoundFragment(parentTab: string, subTab?: string | null, defaultSubTab?: string): string {
  if (!subTab || subTab === defaultSubTab) return parentTab;
  return `${parentTab}-${subTab}`;
}
