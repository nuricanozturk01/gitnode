///
/// Copyright 2026 the original author or authors.
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///      https://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

/** Backend uses `Instant.EPOCH` when a branch has no commits yet (e.g. unborn HEAD). */
const SENTINEL_EPOCH_MS = 0;

const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

const DIVISORS: { amount: Intl.RelativeTimeFormatUnit; ms: number }[] = [
  { amount: 'year', ms: 365 * 24 * 60 * 60 * 1000 },
  { amount: 'month', ms: 30 * 24 * 60 * 60 * 1000 },
  { amount: 'week', ms: 7 * 24 * 60 * 60 * 1000 },
  { amount: 'day', ms: 24 * 60 * 60 * 1000 },
  { amount: 'hour', ms: 60 * 60 * 1000 },
  { amount: 'minute', ms: 60 * 1000 },
  { amount: 'second', ms: 1000 },
];

export function formatRelativeTime(value: string | Date | number | null | undefined): string {
  const ms = parseToEpochMs(value);
  if (ms == null || ms === SENTINEL_EPOCH_MS) return '';

  const deltaMs = ms - Date.now();
  const abs = Math.abs(deltaMs);

  for (const { amount, ms: unitMs } of DIVISORS) {
    if (abs >= unitMs || amount === 'second') {
      const valueRounded = Math.round(deltaMs / unitMs);
      return rtf.format(valueRounded, amount);
    }
  }
  return '';
}

function parseToEpochMs(value: string | Date | number | null | undefined): number | null {
  if (value == null || value === '') return null;
  if (value instanceof Date) {
    const t = value.getTime();
    return Number.isNaN(t) ? null : t;
  }
  if (typeof value === 'number') {
    return value < 1e12 ? value * 1000 : value;
  }
  const trimmed = value.trim();
  if (/^\d+$/.test(trimmed)) {
    const n = Number(trimmed);
    return n < 1e12 ? n * 1000 : n;
  }
  const t = Date.parse(trimmed);
  return Number.isNaN(t) ? null : t;
}
