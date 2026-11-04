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

import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { WorkflowRunService } from '../../../core/actions/services/workflow-run.service';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import type { PullRequestDetail } from '../../../domain/pull-request/models/pull-request-detail.model';

interface TimelineEvent {
  key: string;
  at: number;
  icon: string;
  tone: 'neutral' | 'success' | 'danger' | 'warning' | 'primary';
  text: string;
  detail?: string;
  link?: string[];
}

/** Chronological activity of a pull request: commits, reviews, workflow runs and the final outcome. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-pr-timeline',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './pr-timeline.component.html',
  styleUrl: './pr-timeline.component.css',
})
export class PrTimelineComponent {
  readonly owner = input.required<string>();
  readonly repoName = input.required<string>();
  readonly number = input.required<number>();
  readonly pr = input.required<PullRequestDetail>();
  /** Changes when the pull request is reloaded so the timeline refreshes. */
  readonly refreshKey = input<string>('');

  private readonly prService = inject(PullRequestService);
  private readonly runService = inject(WorkflowRunService);

  private readonly commits = signal<{ sha: string; shortSha: string; message: string; author: string; at: string }[]>(
    [],
  );
  private readonly reviews = signal<
    { id: string; username: string; state: string; body: string | null; at: string | null }[]
  >([]);
  private readonly runs = signal<
    { id: string; name: string; status: string; conclusion: string | null; at: string; number: number }[]
  >([]);
  readonly failed = signal(false);
  readonly expanded = signal(false);
  private requestId = 0;

  readonly events = computed<TimelineEvent[]>(() => {
    const pr = this.pr();
    const list: TimelineEvent[] = [
      {
        key: 'opened',
        at: Date.parse(pr.createdAt),
        icon: 'gitPullRequest',
        tone: 'primary',
        text: `${pr.author.username} opened this pull request`,
      },
    ];
    for (const c of this.commits()) {
      list.push({
        key: `c-${c.sha}`,
        at: Date.parse(c.at),
        icon: 'gitCommit',
        tone: 'neutral',
        text: `${c.author} added a commit`,
        detail: `${c.shortSha} ${c.message}`,
        link: ['/', this.owner(), this.repoName(), 'commit', c.sha],
      });
    }
    for (const r of this.reviews()) {
      if (!r.at) continue;
      const approved = r.state === 'APPROVED';
      const changes = r.state === 'CHANGES_REQUESTED';
      list.push({
        key: `r-${r.id}`,
        at: Date.parse(r.at),
        icon: approved ? 'checkCircle' : 'alertTriangle',
        tone: approved ? 'success' : changes ? 'danger' : 'neutral',
        text: `${r.username} ${approved ? 'approved these changes' : changes ? 'requested changes' : 'reviewed'}`,
        detail: r.body ?? undefined,
      });
    }
    for (const run of this.runs()) {
      const done = run.status === 'SUCCESS' || run.status === 'FAILURE' || run.status === 'CANCELLED';
      list.push({
        key: `w-${run.id}`,
        at: Date.parse(run.at),
        icon: 'workflow',
        tone: run.status === 'SUCCESS' ? 'success' : run.status === 'FAILURE' ? 'danger' : done ? 'warning' : 'neutral',
        text: `Workflow ${run.name} #${run.number}: ${this.runLabel(run.status)}`,
        link: ['/', this.owner(), this.repoName(), 'actions', 'runs', run.id],
      });
    }
    if (pr.mergedAt) {
      list.push({
        key: 'merged',
        at: Date.parse(pr.mergedAt),
        icon: 'gitMerge',
        tone: 'primary',
        text: `${pr.mergedBy?.username ?? 'Someone'} merged this pull request`,
      });
    } else if (pr.closedAt) {
      list.push({
        key: 'closed',
        at: Date.parse(pr.closedAt),
        icon: 'x',
        tone: 'danger',
        text: 'This pull request was closed',
      });
    }
    return list.filter((e) => !Number.isNaN(e.at)).sort((a, b) => a.at - b.at);
  });

  readonly visible = computed(() => {
    const all = this.events();
    return this.expanded() || all.length <= 8 ? all : [...all.slice(0, 2), ...all.slice(-5)];
  });
  readonly hiddenCount = computed(() => this.events().length - this.visible().length);

  constructor() {
    effect(() => {
      this.refreshKey();
      if (this.owner() && this.repoName() && this.number()) void this.load();
    });
  }

  runLabel(status: string): string {
    return (
      (
        {
          SUCCESS: 'passed',
          FAILURE: 'failed',
          CANCELLED: 'cancelled',
          SKIPPED: 'skipped',
          QUEUED: 'queued',
          IN_PROGRESS: 'running',
        } as Record<string, string>
      )[status] ?? status.toLowerCase()
    );
  }

  async load(): Promise<void> {
    const id = ++this.requestId;
    this.failed.set(false);
    const [owner, repo, number] = [this.owner(), this.repoName(), this.number()];
    const sourceRef = `refs/heads/${this.pr().sourceBranch}`;
    const [commits, reviews, runs] = await Promise.allSettled([
      this.prService.getPrCommits(owner, repo, number),
      this.prService.getReviews(owner, repo, number),
      this.runService.listRuns(owner, repo, 0, 30, { event: 'pull_request', triggerRef: sourceRef }),
    ]);
    if (id !== this.requestId) return;
    this.commits.set(
      commits.status === 'fulfilled'
        ? commits.value.map((c) => ({
            sha: c.sha,
            shortSha: c.shortSha,
            message: c.message,
            author: c.author.username || c.author.name,
            at: c.committedAt,
          }))
        : [],
    );
    this.reviews.set(
      reviews.status === 'fulfilled'
        ? reviews.value.reviews.map((r) => ({
            id: r.id,
            username: r.username,
            state: r.state,
            body: r.body,
            at: r.createdAt,
          }))
        : [],
    );
    this.runs.set(
      runs.status === 'fulfilled'
        ? runs.value.content.map((r) => ({
            id: r.id,
            name: r.workflowName,
            status: r.status,
            conclusion: r.conclusion,
            at: r.createdAt,
            number: r.runNumber,
          }))
        : [],
    );
    this.failed.set([commits, reviews, runs].every((r) => r.status === 'rejected'));
  }
}
