import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { DiscussionService } from '../../../core/discussion/services/discussion.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import {
  DISCUSSION_CATEGORIES,
  type DiscussionCategory,
  type DiscussionInfo,
} from '../../../domain/discussion/models/discussion.model';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';

/** Lists one page of discussions at a time; filters and paging are done by the server. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-discussions',
  standalone: true,
  imports: [RouterLink, FormsModule, LucideAngularModule, RelativeTimePipe],
  templateUrl: './discussions.page.html',
})
export class DiscussionsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly discussionService = inject(DiscussionService);
  readonly repoContext = inject(RepoContextService);

  readonly categories = DISCUSSION_CATEGORIES;
  readonly items = signal<DiscussionInfo[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly category = signal<DiscussionCategory | ''>('');
  readonly unanswered = signal(false);
  readonly sort = signal<'latest' | 'updated'>('latest');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  private version = 0;

  private readonly params = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.params().get('owner') ?? '');
  readonly repoName = computed(() => this.params().get('repo') ?? '');
  readonly hasPrev = computed(() => this.page() > 0);
  readonly hasNext = computed(() => this.page() < this.totalPages() - 1);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.version++);
    this.route.parent?.paramMap.subscribe(() => {
      this.page.set(0);
      void this.load();
    });
  }

  categoryLabel(value: DiscussionCategory): string {
    return this.categories.find((c) => c.value === value)?.label ?? value;
  }

  setCategory(value: DiscussionCategory | ''): void {
    this.category.set(value);
    if (value !== 'Q_AND_A' && value !== '') this.unanswered.set(false);
    this.page.set(0);
    void this.load();
  }

  setUnanswered(value: boolean): void {
    this.unanswered.set(value);
    if (value) this.category.set('Q_AND_A');
    this.page.set(0);
    void this.load();
  }

  setSort(value: 'latest' | 'updated'): void {
    this.sort.set(value);
    this.page.set(0);
    void this.load();
  }

  async goTo(page: number): Promise<void> {
    this.page.set(page);
    await this.load();
  }

  async load(): Promise<void> {
    const version = ++this.version;
    if (!this.owner() || !this.repoName()) return;
    this.loading.set(true);
    this.failed.set(false);
    try {
      const result = await this.discussionService.list(this.owner(), this.repoName(), {
        category: this.category(),
        unanswered: this.unanswered(),
        sort: this.sort(),
        page: this.page(),
      });
      if (version !== this.version) return;
      this.items.set(result.content);
      this.totalPages.set(result.totalPages);
      this.totalElements.set(result.totalElements);
    } catch {
      if (version !== this.version) return;
      this.items.set([]);
      this.failed.set(true);
    } finally {
      if (version === this.version) this.loading.set(false);
    }
  }
}
