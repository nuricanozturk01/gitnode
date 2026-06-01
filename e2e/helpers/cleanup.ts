import fs from 'node:fs';
import path from 'node:path';

const E2E_ROOT = path.join(__dirname, '..');

/** Git clone working trees and other ephemeral dirs created during E2E runs. */
const TEMP_DIR_NAMES = ['scenario/.workdirs', '.git-workdirs'] as const;

export function cleanupE2eTempDirs(): void {
  for (const relative of TEMP_DIR_NAMES) {
    fs.rmSync(path.join(E2E_ROOT, relative), { recursive: true, force: true });
  }
}
