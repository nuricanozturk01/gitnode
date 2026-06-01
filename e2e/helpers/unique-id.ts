/** High-entropy suffix for parallel E2E runs (avoids DB unique collisions on same owner). */
export function uniqueE2eSuffix(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Project code prefix: 1–10 chars, starts with letter, uppercase alphanumeric (API validation).
 */
export function uniqueProjectCodePrefix(): string {
  const raw = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
  return `S${raw}`.slice(0, 10);
}

export function uniqueScenarioProjectName(): string {
  return `Scenario project ${uniqueE2eSuffix()}`;
}

export function uniqueScenarioBoardName(): string {
  return `Scenario board ${uniqueE2eSuffix()}`;
}

export function uniqueScenarioColumnName(): string {
  return `Scenario column ${uniqueE2eSuffix()}`;
}
