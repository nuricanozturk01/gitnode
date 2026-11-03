import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { marked } from 'marked';
import {
  paramMapSignal,
  parentParamMapSignal,
  queryParamMapSignal,
} from '../../../core/repo/utils/route-param-signals';
import { ToastService } from '../../../core/toast/toast.service';
import { WikiService } from '../../../core/wiki/services/wiki.service';

const SLUG = /^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/;

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wiki-edit',
  standalone: true,
  imports: [RouterLink, FormsModule],
  templateUrl: './wiki-edit.page.html',
})
export class WikiEditPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly wikiService = inject(WikiService);
  private readonly toast = inject(ToastService);

  readonly slugInput = signal('');
  readonly content = signal('');
  readonly message = signal('');
  readonly baseSha = signal<string | null>(null);
  readonly preview = signal(false);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  private readonly repoParams = parentParamMapSignal(this.route);
  private readonly ownParams = paramMapSignal(this.route);
  private readonly query = queryParamMapSignal(this.route);
  readonly owner = computed(() => this.repoParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoParams().get('repo') ?? '');
  readonly existingSlug = computed(() => this.ownParams().get('slug'));
  readonly isNew = computed(() => !this.existingSlug());
  readonly slug = computed(() => (this.isNew() ? this.slugInput().trim().replace(/\s+/g, '-') : this.existingSlug()!));
  readonly slugValid = computed(() => SLUG.test(this.slug()));
  readonly canSave = computed(() => this.slugValid() && !this.saving() && !this.loading());
  /** Angular sanitizes strings bound to innerHTML. */
  readonly html = computed(() => marked.parse(this.content(), { async: false }) as string);

  constructor() {
    effect(() => {
      const existing = this.existingSlug();
      if (existing && this.owner() && this.repoName()) void this.loadExisting(existing);
      else if (!existing) this.slugInput.set(this.query().get('slug') ?? '');
    });
  }

  private async loadExisting(slug: string): Promise<void> {
    this.loading.set(true);
    try {
      const page = await this.wikiService.get(this.owner(), this.repoName(), slug);
      this.content.set(page.content);
      this.baseSha.set(page.blobSha);
    } catch {
      this.error.set('This page could not be loaded.');
    } finally {
      this.loading.set(false);
    }
  }

  async save(): Promise<void> {
    if (!this.canSave()) return;
    this.saving.set(true);
    this.error.set(null);
    try {
      const saved = await this.wikiService.save(this.owner(), this.repoName(), this.slug(), {
        content: this.content(),
        message: this.message().trim() || undefined,
        baseSha: this.baseSha(),
      });
      this.toast.success('Page saved');
      await this.router.navigate(['/', this.owner(), this.repoName(), 'wiki', saved.slug]);
    } catch (err) {
      const body = (err as { error?: unknown })?.error;
      this.error.set(typeof body === 'string' && body ? body : 'Could not save the page');
    } finally {
      this.saving.set(false);
    }
  }
}
