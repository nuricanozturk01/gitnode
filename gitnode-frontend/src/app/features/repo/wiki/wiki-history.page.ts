import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { paramMapSignal, parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { WikiService } from '../../../core/wiki/services/wiki.service';
import type { WikiRevision } from '../../../domain/wiki/models/wiki.model';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wiki-history',
  standalone: true,
  imports: [RouterLink, RelativeTimePipe],
  templateUrl: './wiki-history.page.html',
})
export class WikiHistoryPage {
  private readonly route = inject(ActivatedRoute);
  private readonly wikiService = inject(WikiService);

  readonly revisions = signal<WikiRevision[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly failed = signal(false);

  private readonly repoParams = parentParamMapSignal(this.route);
  private readonly ownParams = paramMapSignal(this.route);
  readonly owner = computed(() => this.repoParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoParams().get('repo') ?? '');
  readonly slug = computed(() => this.ownParams().get('slug') ?? '');
  readonly hasPrev = computed(() => this.page() > 0);
  readonly hasNext = computed(() => this.page() < this.totalPages() - 1);

  constructor() {
    effect(() => {
      if (this.owner() && this.repoName() && this.slug()) void this.load(0);
    });
  }

  async load(page: number): Promise<void> {
    this.loading.set(true);
    this.failed.set(false);
    try {
      const result = await this.wikiService.history(this.owner(), this.repoName(), this.slug(), page);
      this.revisions.set(result.content);
      this.page.set(result.number);
      this.totalPages.set(result.totalPages);
    } catch {
      this.failed.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
