/** High-entropy suffix for parallel E2E runs (avoids DB unique collisions on same owner). */
export function uniqueE2eSuffix(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Project code prefix: 1–10 chars, starts with letter, uppercase alphanumeric (API validation).
 */
export function uniqueProjectCodePrefix(): string {
  const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  const alnum = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let code = letters[Math.floor(Math.random() * letters.length)];
  for (let i = 1; i < 10; i++) {
    code += alnum[Math.floor(Math.random() * alnum.length)];
  }
  return code;
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
