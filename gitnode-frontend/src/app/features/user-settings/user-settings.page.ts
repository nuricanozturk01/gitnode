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

import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { MarkdownPipe } from '../../shared/pipes/markdown.pipe';
import { SshKeyService } from '../../core/ssh/services/ssh-key.service';
import { ConfirmModalService } from '../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../core/toast/toast.service';
import { profileErrorMessage, apiErrorMessage } from '../../shared/utils/api-error.utils';
import { UserService } from '../../core/user/services/user.service';
import { TokenService } from '../../core/auth/services/token.service';
import { UserWebhookService } from '../../core/webhook/user-webhook.service';
import { RunnerService } from '../../core/actions/services/runner.service';
import { AiService } from '../../core/ai/services/ai.service';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { normalizeStatusKey, runnerStatusLabel } from '../../shared/utils/workflow-status.utils';
import { copyTextToClipboard } from '../../shared/utils/clipboard.util';
import type { SshKeyInfo } from '../../domain/ssh/models/ssh-key-info.model';
import type { WebhookInfo } from '../../domain/webhook/webhook.model';
import type { RunnerInfo, RegistrationToken } from '../../domain/actions/models/runner.model';
import type { AiProvider } from '../../domain/ai/ai-settings.model';
import { USER_WEBHOOK_EVENT_GROUPS } from '../../domain/webhook/webhook.model';
import { parseUrlTab, replaceUrlFragment } from '../../shared/utils/url-tab.utils';
import { environment } from '../../../environments/environment';

type UserSettingsTab = 'profile' | 'security' | 'ssh' | 'webhooks' | 'actions' | 'ai' | 'danger';
const USER_SETTINGS_TABS = [
  'profile',
  'security',
  'ssh',
  'webhooks',
  'actions',
  'ai',
  'danger',
] as const satisfies readonly UserSettingsTab[];
const DEFAULT_USER_SETTINGS_TAB: UserSettingsTab = 'profile';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-user-settings',
  standalone: true,
  imports: [LucideAngularModule, FormsModule, RouterLink, MarkdownPipe, RelativeTimePipe],
  templateUrl: './user-settings.page.html',
  styleUrl: './user-settings.page.css',
})
export class UserSettingsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly urlLocation = inject(Location);
  private readonly sshKeyService = inject(SshKeyService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);
  private readonly userService = inject(UserService);
  private readonly userWebhookService = inject(UserWebhookService);
  private readonly runnerService = inject(RunnerService);
  private readonly aiService = inject(AiService);
  readonly tokenService = inject(TokenService);

  readonly serverUrl = environment.apiUrl;
  statusLabel = runnerStatusLabel;

  readonly runners = signal<RunnerInfo[]>([]);
  readonly loadingRunners = signal(false);
  readonly generatingToken = signal(false);
  readonly registrationToken = signal<RegistrationToken | null>(null);

  isRunnerOnline(status: string): boolean {
    return normalizeStatusKey(status) === 'online';
  }
  isRunnerBusy(status: string): boolean {
    return normalizeStatusKey(status) === 'busy';
  }
  isRunnerOffline(status: string): boolean {
    return normalizeStatusKey(status) === 'offline';
  }

  runnerStartCommand(token: RegistrationToken): string {
    return `./gitnode-runner start \\\n  --server-url ${this.serverUrl} \\\n  --token ${token.token} \\\n  --name my-runner \\\n  --labels self-hosted,linux`;
  }

  readonly activeTab = signal<UserSettingsTab>(DEFAULT_USER_SETTINGS_TAB);

  readonly username = signal('');
  readonly savingUsername = signal(false);
  readonly usernameError = signal<string | null>(null);

  readonly displayName = signal('');
  readonly savingDisplayName = signal(false);
  readonly displayNameError = signal<string | null>(null);

  readonly bio = signal('');
  readonly website = signal('');
  readonly location = signal('');
  readonly profileReadme = signal('');
  readonly savingProfile = signal(false);
  readonly profileError = signal<string | null>(null);

  readonly readmeModalOpen = signal(false);
  readonly readmeModalTab = signal<'edit' | 'preview'>('edit');
  readonly readmeDraft = signal('');

  readonly readmePreviewEmpty = computed(() => !this.readmeDraft().trim());

  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly savingPassword = signal(false);
  readonly passwordError = signal<string | null>(null);

  readonly sshKeys = signal<SshKeyInfo[]>([]);
  readonly sshLoading = signal(true);
  readonly addSshModal = signal(false);
  readonly newSshTitle = signal('');
  readonly newSshKey = signal('');
  readonly addingSsh = signal(false);

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

  readonly webhookEventGroups = USER_WEBHOOK_EVENT_GROUPS;

  get canAddWebhook(): boolean {
    return this.webhooks().length < 3;
  }

  // AI settings state
  readonly aiEnabled = signal(false);
  readonly aiProvider = signal<AiProvider>('OPENAI');
  readonly aiApiKey = signal('');
  readonly aiModel = signal('');
  readonly aiBaseUrl = signal('');
  readonly aiHasKey = signal(false);
  readonly savingAi = signal(false);
  readonly savingAiEnabled = signal(false);
  readonly testingAi = signal(false);
  readonly aiError = signal<string | null>(null);

  readonly aiModelPlaceholder = computed(() => {
    switch (this.aiProvider()) {
      case 'OPENAI':
        return 'gpt-4o-mini';
      case 'ANTHROPIC':
        return 'claude-haiku-4-5-20251001';
      case 'GEMINI':
        return 'gemini-2.0-flash';
      case 'LOCAL':
        return 'llama3';
      default:
        return 'default model';
    }
  });

  constructor() {
    this.route.fragment.pipe(takeUntilDestroyed()).subscribe((fragment) => {
      this.applyTab(parseUrlTab(fragment, USER_SETTINGS_TABS, DEFAULT_USER_SETTINGS_TAB));
    });

    const u = this.tokenService.getUsername();
    if (u) this.username.set(u);
    this.loadProfile();
  }

  private async loadProfile(): Promise<void> {
    try {
      const user = await this.userService.getMe();
      this.username.set(user.username ?? '');
      this.displayName.set(user.displayName ?? '');
      this.bio.set(user.bio ?? '');
      this.website.set(user.website ?? '');
      this.location.set(user.location ?? '');
      this.profileReadme.set(user.profileReadme ?? '');
    } catch {
      // ignore
    }
  }

  setTab(t: UserSettingsTab): void {
    this.applyTab(t);
    replaceUrlFragment(this.urlLocation, t === DEFAULT_USER_SETTINGS_TAB ? null : t);
  }

  private applyTab(t: UserSettingsTab): void {
    if (this.activeTab() === t) return;
    this.activeTab.set(t);
    if (t === 'ssh') this.loadSshKeys();
    if (t === 'webhooks') void this.loadWebhooks();
    if (t === 'actions') void this.loadRunners();
    if (t === 'ai') void this.loadAiSettings();
    if (t === 'profile') {
      const u = this.tokenService.getUsername();
      if (u) this.username.set(u);
      this.loadProfile();
    }
    if (t === 'security') {
      this.currentPassword.set('');
      this.newPassword.set('');
      this.passwordError.set(null);
      const u = this.tokenService.getUsername();
      if (u) this.username.set(u);
      this.usernameError.set(null);
    }
  }

  openReadmeModal(): void {
    this.readmeDraft.set(this.profileReadme());
    this.readmeModalTab.set('edit');
    this.readmeModalOpen.set(true);
  }

  closeReadmeModal(): void {
    this.readmeModalOpen.set(false);
  }

  applyReadme(): void {
    this.profileReadme.set(this.readmeDraft());
    this.readmeModalOpen.set(false);
    void this.saveProfile();
  }

  async saveProfile(): Promise<void> {
    this.savingProfile.set(true);
    this.profileError.set(null);
    try {
      await this.userService.updateProfile({
        bio: this.bio().trim() || null,
        website: this.website().trim() || null,
        location: this.location().trim() || null,
        profileReadme: this.profileReadme().trim() || null,
      });
      this.toast.success('Profile updated');
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to update profile';
      this.profileError.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingProfile.set(false);
    }
  }

  async saveDisplayName(): Promise<void> {
    this.savingDisplayName.set(true);
    this.displayNameError.set(null);
    try {
      await this.userService.updateDisplayName(this.displayName().trim());
      this.toast.success('Display name updated');
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to update display name';
      this.displayNameError.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingDisplayName.set(false);
    }
  }

  async savePassword(): Promise<void> {
    const current = this.currentPassword().trim();
    const newP = this.newPassword().trim();
    if (!current || !newP) return;
    if (newP.length < 6) {
      this.passwordError.set('Password must be at least 6 characters');
      return;
    }
    this.savingPassword.set(true);
    this.passwordError.set(null);
    try {
      await this.userService.changePassword(current, newP);
      this.toast.success('Password updated');
      this.currentPassword.set('');
      this.newPassword.set('');
    } catch (err) {
      const msg = profileErrorMessage(err, 'Failed to update password');
      this.passwordError.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingPassword.set(false);
    }
  }

  async deleteAccount(): Promise<void> {
    const ok = await this.confirmModal.confirm(
      'Delete your account?',
      'This action cannot be undone. All your data will be permanently deleted.',
      { confirmLabel: 'Delete account', variant: 'danger' },
    );
    if (!ok) return;
    try {
      await this.userService.deleteAccount();
      this.tokenService.clearTokens();
      window.location.href = '/';
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to delete account');
    }
  }

  async saveUsername(): Promise<void> {
    const newUsername = this.username().trim();
    if (!newUsername) return;
    const current = this.tokenService.getUsername();
    if (newUsername === current) return;
    const ok = await this.confirmModal.confirm(
      'Update username?',
      `Your username will be changed to @${newUsername}. This affects your profile URL.`,
      { confirmLabel: 'Save', variant: 'primary' },
    );
    if (!ok) return;
    this.savingUsername.set(true);
    this.usernameError.set(null);
    try {
      const user = await this.userService.updateUsername(newUsername);
      this.tokenService.saveTokens({
        token: this.tokenService.getAccessToken()!,
        refreshToken: this.tokenService.getRefreshToken()!,
        expiresIn: 0,
        username: user.username,
      });
      this.username.set(user.username);
      this.toast.success('Username updated');
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to update username';
      this.usernameError.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingUsername.set(false);
    }
  }

  async loadSshKeys(): Promise<void> {
    this.sshLoading.set(true);
    try {
      const data = await this.sshKeyService.listKeys();
      this.sshKeys.set(data);
    } catch {
      this.sshKeys.set([]);
    } finally {
      this.sshLoading.set(false);
    }
  }

  openAddSsh(): void {
    this.newSshTitle.set('');
    this.newSshKey.set('');
    this.addSshModal.set(true);
  }

  closeAddSsh(): void {
    this.addSshModal.set(false);
  }

  async addSshKey(): Promise<void> {
    const title = this.newSshTitle().trim();
    const publicKey = this.newSshKey().trim();
    if (!title || !publicKey) return;
    this.addingSsh.set(true);
    try {
      await this.sshKeyService.addKey({ title, publicKey });
      this.toast.success('SSH key added');
      await this.loadSshKeys();
      this.closeAddSsh();
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to add SSH key');
    } finally {
      this.addingSsh.set(false);
    }
  }

  async deleteSshKey(id: string): Promise<void> {
    const ok = await this.confirmModal.confirm('Remove this SSH key?', undefined, {
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await this.sshKeyService.deleteKey(id);
      this.toast.success('SSH key removed');
      await this.loadSshKeys();
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to remove SSH key');
    }
  }

  async loadWebhooks(): Promise<void> {
    const username = this.tokenService.getUsername();
    if (!username) return;
    this.loadingWebhooks.set(true);
    this.webhookError.set(null);
    try {
      const list = await this.userWebhookService.list(username);
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
    const username = this.tokenService.getUsername();
    if (!username) return;
    const url = this.newWebhookUrl().trim();
    if (!url) return;
    this.savingWebhook.set(true);
    try {
      const editId = this.editingWebhookId();
      if (editId) {
        const updated = await this.userWebhookService.update(username, editId, {
          url,
          secret: this.newWebhookSecret() || undefined,
          enabled: this.newWebhookEnabled(),
          events: this.newWebhookEvents(),
        });
        this.webhooks.set(this.webhooks().map((w) => (w.id === editId ? updated : w)));
        this.toast.success('Webhook updated');
      } else {
        const created = await this.userWebhookService.create(username, {
          url,
          secret: this.newWebhookSecret() || undefined,
          enabled: this.newWebhookEnabled(),
          events: this.newWebhookEvents(),
        });
        this.webhooks.set([...this.webhooks(), created]);
        this.toast.success('Webhook created');
      }
      this.cancelEdit();
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to save webhook');
    } finally {
      this.savingWebhook.set(false);
    }
  }

  async toggleWebhookEnabled(w: WebhookInfo): Promise<void> {
    const username = this.tokenService.getUsername();
    if (!username) return;
    try {
      const updated = await this.userWebhookService.update(username, w.id, { enabled: !w.enabled });
      this.webhooks.set(this.webhooks().map((wh) => (wh.id === w.id ? updated : wh)));
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to update webhook');
    }
  }

  async deleteWebhook(id: string): Promise<void> {
    const username = this.tokenService.getUsername();
    if (!username) return;
    const ok = await this.confirmModal.confirm('Delete this webhook?', undefined, { variant: 'danger' });
    if (!ok) return;
    try {
      await this.userWebhookService.delete(username, id);
      this.webhooks.set(this.webhooks().filter((w) => w.id !== id));
      this.toast.success('Webhook deleted');
      if (this.editingWebhookId() === id) this.cancelEdit();
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to delete webhook');
    }
  }

  async loadRunners(): Promise<void> {
    this.loadingRunners.set(true);
    try {
      this.runners.set(await this.runnerService.list());
    } catch {
      this.runners.set([]);
    } finally {
      this.loadingRunners.set(false);
    }
  }

  async generateToken(): Promise<void> {
    this.generatingToken.set(true);
    try {
      const token = await this.runnerService.createRegistrationToken();
      this.registrationToken.set(token);
      this.toast.success('Registration token generated');
    } catch {
      this.toast.error('Could not generate registration token');
    } finally {
      this.generatingToken.set(false);
    }
  }

  async copyToken(): Promise<void> {
    const token = this.registrationToken()?.token;
    if (!token) return;
    try {
      await copyTextToClipboard(token);
      this.toast.success('Token copied');
    } catch {
      this.toast.error('Could not copy token');
    }
  }

  async copyStartCommand(): Promise<void> {
    const token = this.registrationToken();
    if (!token) return;
    try {
      await copyTextToClipboard(this.runnerStartCommand(token));
      this.toast.success('Start command copied');
    } catch {
      this.toast.error('Could not copy start command');
    }
  }

  async deleteRunner(runner: RunnerInfo): Promise<void> {
    const confirmed = await this.confirmModal.confirm(
      'Remove runner',
      `Remove runner "${runner.name}"? It will stop receiving jobs.`,
      { variant: 'danger' },
    );
    if (!confirmed) return;
    try {
      await this.runnerService.delete(runner.id);
      this.runners.update((list) => list.filter((r) => r.id !== runner.id));
      this.toast.success('Runner removed');
    } catch {
      this.toast.error('Could not remove runner');
    }
  }

  private async loadAiSettings(): Promise<void> {
    if (this.savingAiEnabled()) return;
    try {
      const settings = await this.aiService.getSettings();
      this.aiEnabled.set(settings.enabled);
      this.aiProvider.set(settings.provider);
      this.aiHasKey.set(settings.hasApiKey);
      this.aiModel.set(settings.model ?? '');
      this.aiBaseUrl.set(settings.baseUrl ?? '');
      this.aiError.set(null);
    } catch {
      // ignore — settings may not exist yet
    }
  }

  async onAiEnabledToggle(enabled: boolean): Promise<void> {
    if (this.savingAiEnabled() || this.savingAi()) return;
    const previous = this.aiEnabled();
    if (previous === enabled) return;
    this.aiEnabled.set(enabled);
    await this.persistAiEnabled(enabled, previous);
  }

  private async persistAiEnabled(enabled: boolean, rollbackValue: boolean): Promise<void> {
    this.savingAiEnabled.set(true);
    this.aiError.set(null);
    try {
      const updated = await this.aiService.updateSettings({
        provider: this.aiProvider(),
        apiKey: null,
        baseUrl: this.aiBaseUrl() || null,
        model: this.aiModel() || null,
        enabled,
      });
      this.aiEnabled.set(updated.enabled);
      this.aiHasKey.set(updated.hasApiKey);
      this.toast.success(enabled ? 'AI features enabled' : 'AI features disabled');
    } catch (err) {
      this.aiEnabled.set(rollbackValue);
      const msg = apiErrorMessage(err, 'Failed to update AI settings');
      this.aiError.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingAiEnabled.set(false);
    }
  }

  async saveAiSettings(): Promise<void> {
    const provider = this.aiProvider();
    const needsApiKey = provider !== 'LOCAL';
    if (this.aiEnabled() && needsApiKey && !this.aiHasKey() && !this.aiApiKey().trim()) {
      this.aiError.set('Enter an API key and save before enabling AI features.');
      return;
    }

    this.savingAi.set(true);
    this.aiError.set(null);
    try {
      const updated = await this.aiService.updateSettings({
        provider,
        apiKey: this.aiApiKey() || null,
        baseUrl: this.aiBaseUrl() || null,
        model: this.aiModel() || null,
        enabled: this.aiEnabled(),
      });
      this.aiEnabled.set(updated.enabled);
      this.aiHasKey.set(updated.hasApiKey);
      this.aiApiKey.set('');
      this.toast.success('AI settings saved');
    } catch (err) {
      this.aiError.set(apiErrorMessage(err, 'Failed to save AI settings'));
    } finally {
      this.savingAi.set(false);
    }
  }

  async testAiConnection(): Promise<void> {
    this.testingAi.set(true);
    this.aiError.set(null);
    try {
      const result = await this.aiService.testConnection({
        provider: this.aiProvider(),
        apiKey: this.aiApiKey().trim() || null,
        baseUrl: this.aiBaseUrl() || null,
        model: this.aiModel() || null,
      });
      this.toast.success(result.message || 'AI connection successful!');
    } catch (err) {
      this.aiError.set(apiErrorMessage(err, 'Connection failed. Check your API key and model.'));
    } finally {
      this.testingAi.set(false);
    }
  }

  async deleteAiApiKey(): Promise<void> {
    const confirmed = await this.confirmModal.confirm(
      'Remove API Key',
      'Remove saved API key? AI features will stop working.',
      { confirmLabel: 'Remove', variant: 'danger' },
    );
    if (!confirmed) return;
    try {
      await this.aiService.deleteApiKey();
      this.aiHasKey.set(false);
      this.toast.success('API key removed');
    } catch {
      this.toast.error('Could not remove API key');
    }
  }
}
