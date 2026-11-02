import { ChangeDetectionStrategy, Component, HostListener, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { RepoService } from '../../../core/repo/services/repo.service';
import { ToastService } from '../../../core/toast/toast.service';

@Component({
  selector: 'app-repo-about',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './repo-about.component.html',
})
export class RepoAboutComponent {
  readonly context = inject(RepoContextService);
  private readonly repos = inject(RepoService);
  private readonly toast = inject(ToastService);

  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly description = signal('');
  readonly topics = signal('');
  readonly error = signal('');
  private trigger: HTMLElement | null = null;

  open(event: MouseEvent): void {
    const repo = this.context.repo();
    if (!repo || !this.context.canWriteSettings()) return;
    this.trigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    this.description.set(repo.description ?? '');
    this.topics.set((repo.topics ?? []).join(', '));
    this.error.set('');
    this.editing.set(true);
    setTimeout(() => {
      if (this.editing()) document.getElementById('about-description')?.focus();
    });
  }

  close(): void {
    if (this.saving()) return;
    this.editing.set(false);
    this.trigger?.focus();
    this.trigger = null;
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: Event): void {
    if (!this.editing() || this.saving()) return;
    event.preventDefault();
    this.close();
  }

  onDialogKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab') return;
    const controls = (event.currentTarget as HTMLElement)
      .querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), textarea:not([disabled])');
    const first = controls.item(0);
    const last = controls.item(controls.length - 1);
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }

  async save(): Promise<void> {
    const repo = this.context.repo();
    const owner = repo?.owner?.username;
    if (!repo || !owner || !this.context.canWriteSettings() || this.saving()) return;
    const topics = [...new Set(this.topics().split(',').map((value) => value.trim().toLowerCase()).filter(Boolean))];
    if (topics.length > 6 || topics.some((topic) => topic.length > 50 || !/^[a-z0-9-]+$/.test(topic))) {
      this.error.set('Use up to 6 topics, each up to 50 letters, numbers or hyphens.');
      return;
    }
    this.error.set('');
    this.saving.set(true);
    try {
      const updated = await this.repos.update(owner, repo.name, {
        name: repo.name,
        description: this.description().trim() || undefined,
        topics,
      });
      if (this.context.repo()?.id === repo.id) this.context.repo.set(updated);
      this.saving.set(false);
      this.close();
      this.toast.success('Repository details updated');
    } catch {
      this.error.set('Could not save repository details. Try again.');
    } finally {
      this.saving.set(false);
    }
  }
}
