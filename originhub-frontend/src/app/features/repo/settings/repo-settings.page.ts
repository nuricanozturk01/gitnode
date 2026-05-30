///
/// Copyright 2026 the original author or authors.
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///      https://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Component, inject, signal, computed, effect } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { RepoService } from '../../../core/repo/services/repo.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import { WebhookService } from '../../../core/webhook/webhook.service';
import type { WebhookInfo } from '../../../domain/webhook/webhook.model';
import { WEBHOOK_EVENT_GROUPS } from '../../../domain/webhook/webhook.model';

@Component({
  selector: 'app-repo-settings',
  standalone: true,
  imports: [LucideAngularModule, FormsModule],
  templateUrl: './repo-settings.page.html',
  styleUrl: './repo-settings.page.css',
})
export class RepoSettingsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly repoService = inject(RepoService);
  private readonly repoContext = inject(RepoContextService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);
  private readonly webhookService = inject(WebhookService);

  readonly activeTab = signal<'general' | 'pullRequests' | 'webhooks' | 'danger'>('general');

  readonly generalName = signal('');
  readonly generalDescription = signal('');
  readonly generalTopics = signal<string[]>([]);
  readonly generalIsPrivate = signal(true);
  readonly topicInput = signal('');
  readonly savingGeneral = signal(false);
  readonly generalError = signal<string | null>(null);

  readonly deleteHeadBranchOnPrMerge = signal(false);
  readonly deleteHeadBranchOnPrClose = signal(false);
  readonly savingPullSettings = signal(false);
  readonly pullSettingsError = signal<string | null>(null);

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

  readonly webhookEventGroups = WEBHOOK_EVENT_GROUPS;

  private static readonly MAX_TOPICS = 6;
  private static readonly MAX_WEBHOOKS = 3;

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');

  readonly repo = this.repoContext.repo;

  constructor() {
    effect(() => {
      const r = this.repo();
      const tab = this.activeTab();
      if (!r) return;
      if (tab === 'general') this.syncGeneralFromRepo();
      if (tab === 'pullRequests') this.syncPullSettingsFromRepo();
      if (tab === 'danger') this.syncVisibilityFromRepo();
      if (tab === 'webhooks') void this.loadWebhooks();
    });
  }

  private syncGeneralFromRepo(): void {
    const r = this.repo();
    if (r) {
      this.generalName.set(r.name);
      this.generalDescription.set(r.description ?? '');
      const topics = r.topics;
      this.generalTopics.set(topics?.length ? [...topics] : []);
    }
  }

  private syncVisibilityFromRepo(): void {
    const r = this.repo();
    if (r) {
      this.generalIsPrivate.set(r.isPrivate);
    }
  }

  private syncPullSettingsFromRepo(): void {
    const r = this.repo();
    if (!r) return;
    this.deleteHeadBranchOnPrMerge.set(r.deleteHeadBranchOnPrMerge ?? false);
    this.deleteHeadBranchOnPrClose.set(r.deleteHeadBranchOnPrClose ?? false);
    this.pullSettingsError.set(null);
  }

  addTopic(): void {
    const value = this.topicInput().trim().toLowerCase();
    if (!value) return;
    const topics = this.generalTopics();
    if (topics.length >= RepoSettingsPage.MAX_TOPICS) return;
    if (!/^[a-zA-Z0-9-]+$/.test(value)) return;
    if (topics.includes(value)) return;
    this.generalTopics.set([...topics, value]);
    this.topicInput.set('');
  }

  removeTopic(topic: string): void {
    this.generalTopics.set(this.generalTopics().filter((t) => t !== topic));
  }

  onTopicKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.addTopic();
    }
  }

  setTab(t: 'general' | 'pullRequests' | 'webhooks' | 'danger'): void {
    this.activeTab.set(t);
    const r = this.repo();
    if (!r) return;
    if (t === 'general') this.syncGeneralFromRepo();
    if (t === 'pullRequests') this.syncPullSettingsFromRepo();
    if (t === 'danger') this.syncVisibilityFromRepo();
    if (t === 'webhooks') void this.loadWebhooks();
  }

  onPullMergeToggle(checked: boolean): void {
    this.deleteHeadBranchOnPrMerge.set(checked);
  }

  onPullCloseToggle(checked: boolean): void {
    this.deleteHeadBranchOnPrClose.set(checked);
  }

  async loadWebhooks(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    this.loadingWebhooks.set(true);
    this.webhookError.set(null);
    try {
      const list = await this.webhookService.list(owner, repo);
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
    const owner = this.owner();
    const repo = this.repoName();
    const url = this.newWebhookUrl().trim();
    if (!owner || !repo || !url) return;

    this.savingWebhook.set(true);
    try {
      const editId = this.editingWebhookId();
      if (editId) {
        const updated = await this.webhookService.update(owner, repo, editId, {
          url,
          secret: this.newWebhookSecret() || undefined,
          enabled: this.newWebhookEnabled(),
          events: this.newWebhookEvents(),
        });
        this.webhooks.set(this.webhooks().map((w) => (w.id === editId ? updated : w)));
        this.toast.success('Webhook updated');
      } else {
        const created = await this.webhookService.create(owner, repo, {
          url,
          secret: this.newWebhookSecret() || undefined,
          enabled: this.newWebhookEnabled(),
          events: this.newWebhookEvents(),
        });
        this.webhooks.set([...this.webhooks(), created]);
        this.toast.success('Webhook created');
      }
      this.cancelEdit();
    } catch {
      this.toast.error('Failed to save webhook');
    } finally {
      this.savingWebhook.set(false);
    }
  }

  async toggleWebhookEnabled(w: WebhookInfo): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    try {
      const updated = await this.webhookService.update(owner, repo, w.id, { enabled: !w.enabled });
      this.webhooks.set(this.webhooks().map((wh) => (wh.id === w.id ? updated : wh)));
    } catch {
      this.toast.error('Failed to update webhook');
    }
  }

  async deleteWebhook(id: string): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    const ok = await this.confirmModal.confirm('Delete webhook?', 'This cannot be undone.', {
      confirmLabel: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await this.webhookService.delete(owner, repo, id);
      this.webhooks.set(this.webhooks().filter((w) => w.id !== id));
      this.toast.success('Webhook deleted');
    } catch {
      this.toast.error('Failed to delete webhook');
    }
  }

  get canAddWebhook(): boolean {
    return this.webhooks().length < RepoSettingsPage.MAX_WEBHOOKS;
  }

  async savePullSettings(): Promise<void> {
    const owner = this.owner();
    const repoName = this.repoName();
    const r = this.repo();
    if (!owner || !repoName || !r) return;
    this.savingPullSettings.set(true);
    this.pullSettingsError.set(null);
    try {
      const topics = r.topics?.length ? [...r.topics] : [];
      const updated = await this.repoService.update(owner, repoName, {
        name: r.name,
        description: r.description ?? undefined,
        topics,
        deleteHeadBranchOnPrMerge: this.deleteHeadBranchOnPrMerge(),
        deleteHeadBranchOnPrClose: this.deleteHeadBranchOnPrClose(),
      });
      this.repoContext.repo.set(updated);
      this.toast.success('Pull request settings saved');
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to save settings';
      this.pullSettingsError.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingPullSettings.set(false);
    }
  }

  async saveGeneral(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    const name = this.generalName().trim();
    const description = this.generalDescription().trim() || undefined;
    const topics = this.generalTopics();
    const ok = await this.confirmModal.confirm(
      'Update repository settings?',
      'Name, description, and topics will be saved.',
      {
        confirmLabel: 'Save',
        variant: 'primary',
      },
    );
    if (!ok) return;
    this.savingGeneral.set(true);
    this.generalError.set(null);
    try {
      const updated = await this.repoService.update(owner, repo, {
        name,
        description,
        topics: topics.length > 0 ? topics : [],
      });
      this.repoContext.repo.set(updated);
      this.generalName.set(updated.name);
      this.generalDescription.set(updated.description ?? '');
      const ut = updated.topics;
      this.generalTopics.set(ut?.length ? [...ut] : []);
      this.generalIsPrivate.set(updated.isPrivate);
      if (updated.name !== repo) {
        await this.router.navigate(['/', owner, updated.name, 'settings']);
      }
      this.toast.success('Repository settings updated');
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to update repository';
      this.generalError.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingGeneral.set(false);
    }
  }

  async saveVisibility(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const r = this.repo();
    if (!owner || !repo || !r) return;
    const ok = await this.confirmModal.confirm(
      `Change visibility to ${this.generalIsPrivate() ? 'Private' : 'Public'}?`,
      this.generalIsPrivate()
        ? 'Only you will be able to view and clone this repository.'
        : 'Anyone will be able to view and clone this repository.',
      { confirmLabel: 'Save', variant: 'primary' },
    );
    if (!ok) return;
    this.savingGeneral.set(true);
    this.generalError.set(null);
    try {
      const updated = await this.repoService.update(owner, repo, {
        name: r.name,
        description: r.description ?? undefined,
        topics: r.topics?.length ? [...r.topics] : [],
        isPrivate: this.generalIsPrivate(),
      });
      this.repoContext.repo.set(updated);
      this.generalIsPrivate.set(updated.isPrivate);
      this.toast.success('Visibility updated');
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to update visibility';
      this.generalError.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingGeneral.set(false);
    }
  }

  async deleteRepo(): Promise<void> {
    const repo = this.repoName();
    const ok = await this.confirmModal.confirm(`Are you sure you want to delete "${repo}"?`, 'This cannot be undone.', {
      confirmLabel: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    const owner = this.owner();
    if (!owner || !repo) return;
    try {
      await this.repoService.delete(owner, repo);
      this.toast.success('Repository deleted');
      window.location.href = '/';
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to delete repository');
    }
  }
}
