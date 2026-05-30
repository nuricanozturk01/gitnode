import { Component, inject, signal, computed } from '@angular/core';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { IssueService } from '../../../core/issue/services/issue.service';
import { ToastService } from '../../../core/toast/toast.service';

@Component({
  selector: 'app-new-issue',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, FormsModule],
  templateUrl: './new-issue.page.html',
})
export class NewIssuePage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly issueService = inject(IssueService);
  private readonly toast = inject(ToastService);

  readonly title = signal('');
  readonly description = signal('');
  readonly submitting = signal(false);

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');

  readonly canSubmit = computed(() => this.title().trim().length > 0 && !this.submitting());

  async submit(): Promise<void> {
    if (!this.canSubmit()) return;
    this.submitting.set(true);
    try {
      const issue = await this.issueService.create(this.owner(), this.repoName(), {
        title: this.title().trim(),
        description: this.description().trim() || undefined,
      });
      await this.router.navigate(['/', this.owner(), this.repoName(), 'issues', issue.number]);
    } catch {
      this.toast.error('Failed to create issue');
    } finally {
      this.submitting.set(false);
    }
  }
}
