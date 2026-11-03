import { Component, ChangeDetectionStrategy, inject, signal, computed, OnInit, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Location } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { IssueService } from '../../../core/issue/services/issue.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import type { IssueInfo, IssueLabel, IssueMilestone, IssueStatus } from '../../../domain/repository/models/issue.model';
import { ToastService } from '../../../core/toast/toast.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { parseUrlTab, replaceUrlFragment } from '../../../shared/utils/url-tab.utils';

const ISSUE_TABS = ['open', 'closed'] as const;
type IssueTab = (typeof ISSUE_TABS)[number];
const DEFAULT_ISSUE_TAB: IssueTab = 'open';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-issues',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe, FormsModule],
  templateUrl: './issues.page.html',
})
export class IssuesPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly issueService = inject(IssueService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmModalService);
  readonly repoContext = inject(RepoContextService);

  readonly issues = signal<IssueInfo[]>([]);
  readonly loading = signal(true);
  readonly loadFailed = signal(false);
  readonly tab = signal<IssueTab>(DEFAULT_ISSUE_TAB);
  readonly labels = signal<IssueLabel[]>([]);
  readonly labelFilter = signal('');
  readonly milestones = signal<IssueMilestone[]>([]);
  readonly milestonesFailed = signal(false);
  readonly milestoneFilter = signal('');
  readonly editingMilestone = signal<string | null>(null);
  readonly milestoneTitle = signal('');
  readonly milestoneDescription = signal('');
  readonly milestoneDueDate = signal('');
  readonly milestoneStatus = signal<IssueStatus>('OPEN');
  readonly savingMilestone = signal(false);
  readonly editingLabel = signal<string | null>(null);
  readonly labelName = signal('');
  readonly labelColor = signal('#0969da');
  readonly labelDescription = signal('');
  readonly savingLabel = signal(false);
  private loadVersion = 0;

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');

  readonly hasPrev = computed(() => this.page() > 0);
  readonly hasNext = computed(() => this.page() < this.totalPages() - 1);

  ngOnInit(): void {
    this.route.fragment.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((fragment) => {
      const next = parseUrlTab(fragment, ISSUE_TABS, DEFAULT_ISSUE_TAB);
      if (next === this.tab()) return;
      this.tab.set(next);
      this.page.set(0);
      void this.loadPage();
    });

    this.route.parent!.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.page.set(0);
      this.labelFilter.set('');
      this.milestoneFilter.set('');
      void this.loadLabels();
      void this.loadMilestones();
      void this.loadPage();
    });
  }

  async loadMilestones(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    this.milestonesFailed.set(false);
    try {
      const milestones = await this.issueService.listMilestones(owner, repo);
      if (owner !== this.owner() || repo !== this.repoName()) return;
      this.milestones.set(milestones);
    } catch {
      if (owner !== this.owner() || repo !== this.repoName()) return;
      this.milestones.set([]);
      this.milestonesFailed.set(true);
    }
  }

  filterMilestones(id: string): void {
    this.milestoneFilter.set(id);
    this.page.set(0);
    void this.loadPage();
  }

  editMilestone(milestone: IssueMilestone): void {
    this.editingMilestone.set(milestone.id);
    this.milestoneTitle.set(milestone.title);
    this.milestoneDescription.set(milestone.description ?? '');
    this.milestoneDueDate.set(milestone.dueDate ?? '');
    this.milestoneStatus.set(milestone.status);
  }

  resetMilestone(): void {
    this.editingMilestone.set(null);
    this.milestoneTitle.set('');
    this.milestoneDescription.set('');
    this.milestoneDueDate.set('');
    this.milestoneStatus.set('OPEN');
  }

  async saveMilestone(): Promise<void> {
    if (!this.milestoneTitle().trim() || this.savingMilestone()) return;
    this.savingMilestone.set(true);
    try {
      await this.issueService.saveMilestone(
        this.owner(),
        this.repoName(),
        {
          title: this.milestoneTitle().trim(),
          description: this.milestoneDescription().trim() || null,
          dueDate: this.milestoneDueDate() || null,
          status: this.milestoneStatus(),
        },
        this.editingMilestone() ?? undefined,
      );
      this.resetMilestone();
      await this.loadMilestones();
      await this.loadPage();
    } catch {
      this.toast.error('Could not save milestone. Check its title and try again.');
    } finally {
      this.savingMilestone.set(false);
    }
  }

  async deleteMilestone(milestone: IssueMilestone): Promise<void> {
    if (
      !(await this.confirm.confirm('Delete milestone', `Delete milestone "${milestone.title}"? Issues will remain.`, {
        confirmLabel: 'Delete',
        variant: 'danger',
      }))
    )
      return;
    try {
      await this.issueService.deleteMilestone(this.owner(), this.repoName(), milestone.id);
      if (this.milestoneFilter() === milestone.id) this.milestoneFilter.set('');
      if (this.editingMilestone() === milestone.id) this.resetMilestone();
      await this.loadMilestones();
      await this.loadPage();
    } catch {
      this.toast.error('Failed to delete milestone');
    }
  }

  async loadLabels(): Promise<void> {
    try {
      this.labels.set(await this.issueService.listLabels(this.owner(), this.repoName()));
    } catch {
      this.labels.set([]);
      this.toast.error('Failed to load labels');
    }
  }

  filterLabels(id: string): void {
    this.labelFilter.set(id);
    this.page.set(0);
    void this.loadPage();
  }

  editLabel(label: IssueLabel): void {
    this.editingLabel.set(label.id);
    this.labelName.set(label.name);
    this.labelColor.set(`#${label.color}`);
    this.labelDescription.set(label.description ?? '');
  }

  resetLabel(): void {
    this.editingLabel.set(null);
    this.labelName.set('');
    this.labelColor.set('#0969da');
    this.labelDescription.set('');
  }

  async saveLabel(): Promise<void> {
    if (!this.labelName().trim() || this.savingLabel()) return;
    this.savingLabel.set(true);
    try {
      await this.issueService.saveLabel(
        this.owner(),
        this.repoName(),
        {
          name: this.labelName().trim(),
          color: this.labelColor().slice(1),
          description: this.labelDescription().trim(),
        },
        this.editingLabel() ?? undefined,
      );
      this.resetLabel();
      await this.loadLabels();
      await this.loadPage();
    } catch {
      this.toast.error('Could not save label. Check its name and try again.');
    } finally {
      this.savingLabel.set(false);
    }
  }

  async deleteLabel(label: IssueLabel): Promise<void> {
    if (
      !(await this.confirm.confirm('Delete label', `Delete label "${label.name}" from this repository?`, {
        confirmLabel: 'Delete',
        variant: 'danger',
      }))
    )
      return;
    try {
      await this.issueService.deleteLabel(this.owner(), this.repoName(), label.id);
      if (this.labelFilter() === label.id) this.labelFilter.set('');
      if (this.editingLabel() === label.id) this.resetLabel();
      await this.loadLabels();
      await this.loadPage();
    } catch {
      this.toast.error('Failed to delete label');
    }
  }

  setTab(t: IssueTab): void {
    this.tab.set(t);
    this.page.set(0);
    replaceUrlFragment(this.location, t === DEFAULT_ISSUE_TAB ? null : t);
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

  retryLoad(): void {
    void this.loadPage();
  }

  private async loadPage(): Promise<void> {
    const version = ++this.loadVersion;
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.loadFailed.set(false);
    const status: IssueStatus = this.tab() === 'open' ? 'OPEN' : 'CLOSED';
    try {
      const result = await this.issueService.getAll(
        owner,
        repo,
        status,
        this.page(),
        this.labelFilter(),
        this.milestoneFilter(),
      );
      if (version !== this.loadVersion) return;
      this.issues.set(result.content);
      this.totalPages.set(result.totalPages);
      this.totalElements.set(result.totalElements);
    } catch {
      if (version !== this.loadVersion) return;
      this.issues.set([]);
      this.loadFailed.set(true);
    } finally {
      if (version === this.loadVersion) this.loading.set(false);
    }
  }
}
