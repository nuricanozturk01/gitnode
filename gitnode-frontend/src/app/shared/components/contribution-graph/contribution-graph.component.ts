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

import { Component, ChangeDetectionStrategy, computed, input, signal } from '@angular/core';
import type { ContributionDay, UserContributionsResponse } from '../../../domain/profile/models/contribution.model';

interface GraphWeek {
  key: string;
  days: (ContributionDay | null)[];
  monthLabel: string | null;
}

interface TooltipState {
  day: ContributionDay;
  x: number;
  y: number;
}

const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'] as const;
const MONTH_FORMAT = new Intl.DateTimeFormat(undefined, { month: 'short' });
const DAY_FORMAT = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  month: 'short',
  day: 'numeric',
  year: 'numeric',
});

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-contribution-graph',
  standalone: true,
  templateUrl: './contribution-graph.component.html',
  styleUrl: './contribution-graph.component.css',
})
export class ContributionGraphComponent {
  readonly data = input<UserContributionsResponse | null>(null);
  readonly loading = input(false);
  readonly loadFailed = input(false);
  readonly username = input('');

  readonly tooltip = signal<TooltipState | null>(null);

  readonly weekdayLabels = computed(() => [WEEKDAY_LABELS[1], WEEKDAY_LABELS[3], WEEKDAY_LABELS[5]]);

  readonly weeks = computed(() => this.buildWeeks(this.data()));

  readonly legendLevels = [0, 1, 2, 3, 4] as const;

  showTooltip(day: ContributionDay, event: MouseEvent | FocusEvent): void {
    const point = tooltipPoint(event);
    this.tooltip.set({ day, x: point.x, y: point.y });
  }

  moveTooltip(event: MouseEvent): void {
    const current = this.tooltip();
    if (!current) return;
    this.tooltip.set({ ...current, x: event.clientX, y: event.clientY });
  }

  hideTooltip(): void {
    this.tooltip.set(null);
  }

  formatDayLabel(day: ContributionDay): string {
    return DAY_FORMAT.format(parseIsoDate(day.date));
  }

  contributionLabel(count: number): string {
    return count === 1 ? '1 contribution' : `${count} contributions`;
  }

  breakdownLines(day: ContributionDay): string[] {
    const lines: string[] = [];
    const b = day.breakdown;
    if (b.issues > 0) lines.push(formatCount(b.issues, 'issue opened', 'issues opened'));
    if (b.issueComments > 0) lines.push(formatCount(b.issueComments, 'issue comment', 'issue comments'));
    if (b.pullRequests > 0) lines.push(formatCount(b.pullRequests, 'pull request opened', 'pull requests opened'));
    if (b.pullRequestComments > 0) {
      lines.push(formatCount(b.pullRequestComments, 'PR comment', 'PR comments'));
    }
    if (b.pullRequestMerges > 0)
      lines.push(formatCount(b.pullRequestMerges, 'pull request merged', 'pull requests merged'));
    if (b.releases > 0) lines.push(formatCount(b.releases, 'release published', 'releases published'));
    if (b.snippets > 0) lines.push(formatCount(b.snippets, 'snippet created', 'snippets created'));
    if (b.snippetRevisions > 0) lines.push(formatCount(b.snippetRevisions, 'snippet edit', 'snippet edits'));
    if (b.snippetComments > 0) lines.push(formatCount(b.snippetComments, 'snippet comment', 'snippet comments'));
    return lines;
  }

  private buildWeeks(data: UserContributionsResponse | null): GraphWeek[] {
    if (!data) return [];

    const rangeStart = parseIsoDate(data.rangeStart);
    const rangeEnd = parseIsoDate(data.rangeEnd);
    const dayMap = new Map(data.days.map((day) => [day.date, day]));

    const gridStart = new Date(rangeStart);
    while (gridStart.getUTCDay() !== 0) {
      gridStart.setUTCDate(gridStart.getUTCDate() - 1);
    }

    const weeks: GraphWeek[] = [];
    const cursor = new Date(gridStart);
    let previousMonth = -1;

    while (cursor <= rangeEnd) {
      const weekStart = new Date(cursor);
      const days: (ContributionDay | null)[] = [];

      for (let offset = 0; offset < 7; offset++) {
        const current = new Date(cursor);
        current.setUTCDate(weekStart.getUTCDate() + offset);

        if (current < rangeStart || current > rangeEnd) {
          days.push(null);
          continue;
        }

        const key = formatIsoDate(current);
        days.push(
          dayMap.get(key) ?? {
            date: key,
            count: 0,
            level: 0,
            breakdown: emptyBreakdown(),
          },
        );
      }

      const month = weekStart.getUTCMonth();
      const monthLabel = month !== previousMonth ? MONTH_FORMAT.format(weekStart) : null;
      previousMonth = month;

      weeks.push({
        key: formatIsoDate(weekStart),
        days,
        monthLabel,
      });

      cursor.setUTCDate(cursor.getUTCDate() + 7);
    }

    return weeks;
  }
}

function parseIsoDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

function formatIsoDate(date: Date): string {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatCount(count: number, singular: string, plural: string): string {
  return count === 1 ? `1 ${singular}` : `${count} ${plural}`;
}

function emptyBreakdown() {
  return {
    issues: 0,
    issueComments: 0,
    pullRequests: 0,
    pullRequestComments: 0,
    pullRequestMerges: 0,
    releases: 0,
    snippets: 0,
    snippetRevisions: 0,
    snippetComments: 0,
  };
}

function tooltipPoint(event: MouseEvent | FocusEvent): { x: number; y: number } {
  if (event instanceof MouseEvent) {
    return { x: event.clientX, y: event.clientY };
  }

  const target = event.target;
  if (!(target instanceof HTMLElement)) {
    return { x: 0, y: 0 };
  }

  const rect = target.getBoundingClientRect();
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height };
}
