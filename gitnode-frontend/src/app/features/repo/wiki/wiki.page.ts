import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { marked } from 'marked';
import { LucideAngularModule } from 'lucide-angular';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import {
  paramMapSignal,
  parentParamMapSignal,
  queryParamMapSignal,
} from '../../../core/repo/utils/route-param-signals';
import { ToastService } from '../../../core/toast/toast.service';
import { WikiService } from '../../../core/wiki/services/wiki.service';
import type { WikiPage as WikiPageModel, WikiPageSummary } from '../../../domain/wiki/models/wiki.model';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';

const HOME = 'Home';

/** Shows one wiki page next to a paged page list. Contents are fetched per page, never in bulk. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wiki',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './wiki.page.html',
})
export class WikiPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly wikiService = inject(WikiService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);
  readonly repoContext = inject(RepoContextService);

  readonly pages = signal<WikiPageSummary[]>([]);
  readonly pagesPage = signal(0);
  readonly pagesTotal = signal(0);
  readonly pagesLoading = signal(false);
  readonly page = signal<WikiPageModel | null>(null);
  readonly loading = signal(true);
  readonly missing = signal(false);
  readonly failed = signal(false);
  private version = 0;

  private readonly repoParams = parentParamMapSignal(this.route);
  private readonly ownParams = paramMapSignal(this.route);
  private readonly query = queryParamMapSignal(this.route);
  readonly owner = computed(() => this.repoParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoParams().get('repo') ?? '');
  readonly slug = computed(() => this.ownParams().get('slug') ?? HOME);
  readonly rev = computed(() => this.query().get('rev') ?? undefined);
  readonly canWrite = computed(() => this.repoContext.isLoggedIn() && this.repoContext.hasPermission('PUSH'));
  readonly hasMorePages = computed(() => this.pages().length < this.pagesTotal());
  /** Angular sanitizes strings bound to innerHTML, so page authors cannot inject scripts. */
  readonly html = computed(() => marked.parse(this.page()?.content ?? '', { async: false }) as string);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.version++);
    effect(() => {
      if (this.owner() && this.repoName()) void this.loadPages(0);
    });
    effect(() => {
      if (this.owner() && this.repoName()) void this.loadPage(this.slug(), this.rev());
    });
  }

  async loadPages(page: number): Promise<void> {
    this.pagesLoading.set(true);
    try {
      const result = await this.wikiService.list(this.owner(), this.repoName(), page);
      this.pages.update((current) => (page === 0 ? result.content : [...current, ...result.content]));
      this.pagesPage.set(result.number);
      this.pagesTotal.set(result.totalElements);
    } catch {
      this.toast.error('Could not load the wiki pages');
    } finally {
      this.pagesLoading.set(false);
    }
  }

  private async loadPage(slug: string, rev: string | undefined): Promise<void> {
    const version = ++this.version;
    this.loading.set(true);
    this.missing.set(false);
    this.failed.set(false);
    try {
      const page = await this.wikiService.get(this.owner(), this.repoName(), slug, rev);
      if (version === this.version) this.page.set(page);
    } catch (err) {
      if (version !== this.version) return;
      this.page.set(null);
      if (err instanceof HttpErrorResponse && err.status === 404) this.missing.set(true);
      else this.failed.set(true);
    } finally {
      if (version === this.version) this.loading.set(false);
    }
  }

  async deletePage(): Promise<void> {
    const page = this.page();
    if (!page) return;
    const ok = await this.confirmModal.confirm(
      'Delete this page?',
      `"${page.title}" and its history entry stay in Git.`,
      {
        confirmLabel: 'Delete',
        variant: 'danger',
      },
    );
    if (!ok) return;
    try {
      await this.wikiService.delete(this.owner(), this.repoName(), page.slug);
      this.toast.success('Page deleted');
      await this.router.navigate(['/', this.owner(), this.repoName(), 'wiki']);
      void this.loadPages(0);
    } catch {
      this.toast.error('Could not delete the page');
    }
  }
}
