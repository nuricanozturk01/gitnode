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

import { Pipe, PipeTransform } from '@angular/core';
import moment, { type Moment } from 'moment';

/** Backend uses `Instant.EPOCH` when a branch has no commits yet (e.g. unborn HEAD). */
const SENTINEL_EPOCH_MS = 0;

@Pipe({ name: 'relativeTime', standalone: true })
export class RelativeTimePipe implements PipeTransform {
  transform(value: string | Date | number | null | undefined): string {
    const m = this.parse(value);
    if (m == null || !m.isValid()) return '';
    if (m.valueOf() === SENTINEL_EPOCH_MS) return '';
    return m.fromNow();
  }

  private parse(value: string | Date | number | null | undefined): Moment | null {
    if (value == null || value === '') return null;
    if (value instanceof Date) return moment(value);
    if (typeof value === 'number') {
      return value < 1e12 ? moment.unix(value) : moment(value);
    }
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (/^\d+$/.test(trimmed)) {
        const n = Number(trimmed);
        return n < 1e12 ? moment.unix(n) : moment(n);
      }
      return moment(trimmed);
    }
    return null;
  }
}
