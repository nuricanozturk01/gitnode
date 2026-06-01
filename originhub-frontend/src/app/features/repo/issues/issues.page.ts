import { Component, ChangeDetectionStrategy, inject, signal, computed, OnInit, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { IssueService } from '../../../core/issue/services/issue.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import type { IssueInfo, IssueStatus } from '../../../domain/repository/models/issue.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-issues',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './issues.page.html',
})
export class IssuesPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly issueService = inject(IssueService);
  private readonly destroyRef = inject(DestroyRef);
  readonly repoContext = inject(RepoContextService);

  readonly issues = signal<IssueInfo[]>([]);
  readonly loading = signal(true);
  readonly tab = signal<'open' | 'closed'>('open');

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');

  readonly hasPrev = computed(() => this.page() > 0);
  readonly hasNext = computed(() => this.page() < this.totalPages() - 1);

  ngOnInit(): void {
    this.route.parent!.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.page.set(0);
      void this.loadPage();
    });
  }

  setTab(t: 'open' | 'closed'): void {
    this.tab.set(t);
    this.page.set(0);
    void this.loadPage();
  }

  async prevPage(): Promise<void> {
    if (!this.hasPrev()) return;
    this.page.update((p) => p - 1);
    await this.loadPage();
  }

  async nextPage(): Promise<void> {
    if (!this.hasNext()) return;
    this.page.update((p) => p + 1);
    await this.loadPage();
  }

  private async loadPage(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    const status: IssueStatus = this.tab() === 'open' ? 'OPEN' : 'CLOSED';
    try {
      const result = await this.issueService.getAll(owner, repo, status, this.page());
      this.issues.set(result.content);
      this.totalPages.set(result.totalPages);
      this.totalElements.set(result.totalElements);
    } catch {
      this.issues.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}
