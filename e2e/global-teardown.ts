import { cleanupE2eTempDirs } from './helpers/cleanup';

/** Safety net when the teardown project does not run (e.g. api-only). */
export default function globalTeardown(): void {
  cleanupE2eTempDirs();
}
