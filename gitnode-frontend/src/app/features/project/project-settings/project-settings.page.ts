import { Component, ChangeDetectionStrategy, DestroyRef, inject, signal, OnInit, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Location } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ProjectService } from '../../../core/project/services/project.service';
import { ProjectWebhookService } from '../../../core/webhook/project-webhook.service';
import { RepoService } from '../../../core/repo/services/repo.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { ToastService } from '../../../core/toast/toast.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import type { WebhookInfo } from '../../../domain/webhook/webhook.model';
import { PROJECT_WEBHOOK_EVENT_GROUPS } from '../../../domain/webhook/webhook.model';
import type { ProjectRepoInfo } from '../../../domain/project/models/project-repo-info.model';
import type { RepoInfo } from '../../../domain/repository/models/repo-info.model';
import { parseUrlTab, replaceUrlFragment } from '../../../shared/utils/url-tab.utils';

type ProjectSettingsTab = 'general' | 'webhooks' | 'repos';
const PROJECT_SETTINGS_TABS = ['general', 'webhooks', 'repos'] as const satisfies readonly ProjectSettingsTab[];
const DEFAULT_PROJECT_SETTINGS_TAB: ProjectSettingsTab = 'general';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-project-settings',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './project-settings.page.html',
})
export class ProjectSettingsPage implements OnInit {
  private static readonly MAX_WEBHOOKS = 3;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly projectService = inject(ProjectService);
  private readonly projectWebhookService = inject(ProjectWebhookService);
  private readonly repoService = inject(RepoService);
  private readonly tokenService = inject(TokenService);
  private readonly toastService = inject(ToastService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly destroyRef = inject(DestroyRef);

  readonly activeTab = signal<ProjectSettingsTab>(DEFAULT_PROJECT_SETTINGS_TAB);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly projectName = signal('');
  readonly syncTaskStatusOnPrMerge = signal(true);
  readonly isPublic = signal(false);

  // Webhook state
  readonly webhooks = signal<WebhookInfo[]>([]);
  readonly loadingWebhooks = signal(false);
  readonly webhookError = signal<string | null>(null);
  readonly showAddWebhook = signal(false);
  readonly savingWebhook = signal(false);
  readonly editingWebhookId = signal<string | null>(null);

  readonly newWebhookUrl = signal('');
  readonly newWebhookSecret = signal('');
  readonly newWebhookEnabled = signal(true);
  readonly newWebhookEvents = signal<string[]>([]);

  readonly webhookEventGroups = PROJECT_WEBHOOK_EVENT_GROUPS;

  readonly canAddWebhook = computed(() => this.webhooks().length < ProjectSettingsPage.MAX_WEBHOOKS);

  // Repos tab state
  readonly linkedRepos = signal<ProjectRepoInfo[]>([]);
  readonly loadingRepos = signal(false);
  readonly repoError = signal<string | null>(null);
  readonly allUserRepos = signal<RepoInfo[]>([]);
  readonly repoSearch = signal('');
  readonly repoDropdownOpen = signal(false);
  readonly linkingRepo = signal(false);

  readonly availableRepos = computed(() => {
    const linked = new Set(this.linkedRepos().map((r) => r.id));
    const search = this.repoSearch().toLowerCase();
    return this.allUserRepos()
      .filter((r) => !linked.has(r.id))
      .filter((r) => !search || r.name.toLowerCase().includes(search));
  });

  get owner(): string {
    return this.route.snapshot.paramMap.get('owner') ?? '';
  }

  get projectCode(): string {
    return this.route.snapshot.paramMap.get('projectCode') ?? '';
  }

  async ngOnInit(): Promise<void> {
    this.route.fragment.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((fragment) => {
      this.applyTab(parseUrlTab(fragment, PROJECT_SETTINGS_TABS, DEFAULT_PROJECT_SETTINGS_TAB));
    });

    this.loading.set(true);
    try {
      const p = await this.projectService.get(this.owner, this.projectCode);
      this.projectName.set(p.name);
      this.syncTaskStatusOnPrMerge.set(p.syncTaskStatusOnPrMerge ?? true);
      this.isPublic.set(p.isPublic ?? false);
    } catch {
      this.toastService.error('Failed to load project');
      void this.router.navigate(['/projects', this.owner, this.projectCode]);
    } finally {
      this.loading.set(false);
    }
  }

  setTab(t: ProjectSettingsTab): void {
    this.applyTab(t);
    replaceUrlFragment(this.location, t === DEFAULT_PROJECT_SETTINGS_TAB ? null : t);
  }

  private applyTab(t: ProjectSettingsTab): void {
    if (this.activeTab() === t) return;
    this.activeTab.set(t);
    if (t === 'webhooks') void this.loadWebhooks();
    if (t === 'repos') void this.loadRepos();
  }

  onSyncChange(checked: boolean): void {
    this.syncTaskStatusOnPrMerge.set(checked);
  }

  onVisibilityChange(checked: boolean): void {
    this.isPublic.set(checked);
  }

  async save(): Promise<void> {
    this.saving.set(true);
    try {
      await this.projectService.update(this.owner, this.projectCode, {
        syncTaskStatusOnPrMerge: this.syncTaskStatusOnPrMerge(),
        isPublic: this.isPublic(),
      });
      this.toastService.success('Settings saved');
    } catch {
      this.toastService.error('Failed to save settings');
    } finally {
      this.saving.set(false);
    }
  }

  // ── Webhooks ──────────────────────────────────────────────────────────

  async loadWebhooks(): Promise<void> {
    if (!this.owner || !this.projectCode) return;
    this.loadingWebhooks.set(true);
    this.webhookError.set(null);
    try {
      const list = await this.projectWebhookService.list(this.owner, this.projectCode);
      this.webhooks.set(list);
    } catch {
      this.webhookError.set('Failed to load webhooks');
    } finally {
      this.loadingWebhooks.set(false);
    }
  }

  toggleWebhookEvent(key: string): void {
    const current = this.newWebhookEvents();
    if (current.includes(key)) {
      this.newWebhookEvents.set(current.filter((e) => e !== key));
    } else {
      this.newWebhookEvents.set([...current, key]);
    }
  }

  isEventSelected(key: string): boolean {
    return this.newWebhookEvents().includes(key);
  }

  startEditWebhook(w: WebhookInfo): void {
    this.editingWebhookId.set(w.id);
    this.showAddWebhook.set(false);
    this.newWebhookUrl.set(w.url);
    this.newWebhookSecret.set('');
    this.newWebhookEnabled.set(w.enabled);
    this.newWebhookEvents.set([...w.events]);
  }

  cancelEdit(): void {
    this.editingWebhookId.set(null);
    this.showAddWebhook.set(false);
    this.newWebhookUrl.set('');
    this.newWebhookSecret.set('');
    this.newWebhookEnabled.set(true);
    this.newWebhookEvents.set([]);
  }

  async saveWebhook(): Promise<void> {
    const url = this.newWebhookUrl().trim();
    if (!url) return;

    this.savingWebhook.set(true);
    try {
      const editId = this.editingWebhookId();
      if (editId) {
        const updated = await this.projectWebhookService.update(this.owner, this.projectCode, editId, {
          url,
          secret: this.newWebhookSecret() || undefined,
          enabled: this.newWebhookEnabled(),
          events: this.newWebhookEvents(),
        });
        this.webhooks.set(this.webhooks().map((w) => (w.id === editId ? updated : w)));
        this.toastService.success('Webhook updated');
      } else {
        const created = await this.projectWebhookService.create(this.owner, this.projectCode, {
          url,
          secret: this.newWebhookSecret() || undefined,
          enabled: this.newWebhookEnabled(),
          events: this.newWebhookEvents(),
        });
        this.webhooks.set([...this.webhooks(), created]);
        this.toastService.success('Webhook created');
      }
      this.cancelEdit();
    } catch {
      this.toastService.error('Failed to save webhook');
    } finally {
      this.savingWebhook.set(false);
    }
  }

  async toggleWebhookEnabled(w: WebhookInfo): Promise<void> {
    try {
      const updated = await this.projectWebhookService.update(this.owner, this.projectCode, w.id, {
        enabled: !w.enabled,
      });
      this.webhooks.set(this.webhooks().map((wh) => (wh.id === w.id ? updated : wh)));
    } catch {
      this.toastService.error('Failed to update webhook');
    }
  }

  async deleteWebhook(id: string): Promise<void> {
    const ok = await this.confirmModal.confirm('Delete webhook?', 'This cannot be undone.', {
      confirmLabel: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await this.projectWebhookService.delete(this.owner, this.projectCode, id);
      this.webhooks.set(this.webhooks().filter((w) => w.id !== id));
      this.toastService.success('Webhook deleted');
    } catch {
      this.toastService.error('Failed to delete webhook');
    }
  }

  // ── Repos ────────────────────────────────────────────────────────────────

  async loadRepos(): Promise<void> {
    if (!this.owner || !this.projectCode) return;
    this.loadingRepos.set(true);
    this.repoError.set(null);
    try {
      const [linked, userRepos] = await Promise.all([
        this.projectService.getLinkedRepos(this.owner, this.projectCode),
        this.repoService.listUserRepos(this.owner, 0, 100),
      ]);
      this.linkedRepos.set(linked);
      this.allUserRepos.set(userRepos.content);
    } catch {
      this.repoError.set('Failed to load repositories');
    } finally {
      this.loadingRepos.set(false);
    }
  }

  async linkRepo(repo: RepoInfo): Promise<void> {
    this.repoDropdownOpen.set(false);
    this.repoSearch.set('');
    this.linkingRepo.set(true);
    try {
      await this.projectService.linkRepo(this.owner, this.projectCode, repo.id);
      await this.loadRepos();
      this.toastService.success(`${repo.name} linked`);
    } catch (err: unknown) {
      const msg = (err as { error?: { message?: string } }).error?.message;
      this.toastService.error(msg ?? 'Failed to link repository');
    } finally {
      this.linkingRepo.set(false);
    }
  }

  async unlinkRepo(repo: ProjectRepoInfo): Promise<void> {
    const ok = await this.confirmModal.confirm(
      `Unlink ${repo.name}?`,
      'The repository will no longer be associated with this project.',
      { confirmLabel: 'Unlink', variant: 'danger' },
    );
    if (!ok) return;
    try {
      await this.projectService.unlinkRepo(this.owner, this.projectCode, repo.id);
      this.linkedRepos.set(this.linkedRepos().filter((r) => r.id !== repo.id));
      this.toastService.success(`${repo.name} unlinked`);
    } catch {
      this.toastService.error('Failed to unlink repository');
    }
  }
}
