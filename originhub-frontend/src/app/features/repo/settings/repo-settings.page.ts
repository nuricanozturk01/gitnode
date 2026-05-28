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

  readonly activeTab = signal<'general' | 'pullRequests' | 'danger'>('general');

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

  private static readonly MAX_TOPICS = 6;

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

  setTab(t: 'general' | 'pullRequests' | 'danger'): void {
    this.activeTab.set(t);
    const r = this.repo();
    if (!r) return;
    if (t === 'general') this.syncGeneralFromRepo();
    if (t === 'pullRequests') this.syncPullSettingsFromRepo();
    if (t === 'danger') this.syncVisibilityFromRepo();
  }

  onPullMergeToggle(checked: boolean): void {
    this.deleteHeadBranchOnPrMerge.set(checked);
  }

  onPullCloseToggle(checked: boolean): void {
    this.deleteHeadBranchOnPrClose.set(checked);
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
