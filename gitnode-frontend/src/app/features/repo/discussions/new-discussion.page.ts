import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DiscussionService } from '../../../core/discussion/services/discussion.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { ToastService } from '../../../core/toast/toast.service';
import { DISCUSSION_CATEGORIES, type DiscussionCategory } from '../../../domain/discussion/models/discussion.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-new-discussion',
  standalone: true,
  imports: [RouterLink, FormsModule],
  templateUrl: './new-discussion.page.html',
})
export class NewDiscussionPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly discussionService = inject(DiscussionService);
  private readonly toast = inject(ToastService);
  private readonly repoContext = inject(RepoContextService);

  readonly title = signal('');
  readonly body = signal('');
  readonly category = signal<DiscussionCategory>('GENERAL');
  readonly submitting = signal(false);

  private readonly params = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.params().get('owner') ?? '');
  readonly repoName = computed(() => this.params().get('repo') ?? '');
  /** Announcements are for maintainers; the server enforces it, the form just hides the choice. */
  readonly categories = computed(() =>
    DISCUSSION_CATEGORIES.filter((c) => c.value !== 'ANNOUNCEMENTS' || this.repoContext.hasPermission('ISSUE_MANAGE')),
  );
  readonly canSubmit = computed(() => this.title().trim().length > 0 && !this.submitting());

  async submit(): Promise<void> {
    if (!this.canSubmit()) return;
    this.submitting.set(true);
    try {
      const created = await this.discussionService.create(this.owner(), this.repoName(), {
        title: this.title().trim(),
        body: this.body().trim(),
        category: this.category(),
      });
      await this.router.navigate(['/', this.owner(), this.repoName(), 'discussions', created.info.number]);
    } catch {
      this.toast.error('Could not start the discussion');
    } finally {
      this.submitting.set(false);
    }
  }
}
