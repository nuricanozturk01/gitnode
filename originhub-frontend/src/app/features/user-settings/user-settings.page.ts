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
import { FormsModule } from '@angular/forms';
import { MarkdownPipe } from '../../shared/pipes/markdown.pipe';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { SshKeyService } from '../../core/ssh/services/ssh-key.service';
import { ConfirmModalService } from '../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../core/toast/toast.service';
import { UserService } from '../../core/user/services/user.service';
import { TokenService } from '../../core/auth/services/token.service';
import { UserWebhookService } from '../../core/webhook/user-webhook.service';
import type { SshKeyInfo } from '../../domain/ssh/models/ssh-key-info.model';
import type { WebhookInfo } from '../../domain/webhook/webhook.model';
import { USER_WEBHOOK_EVENT_GROUPS } from '../../domain/webhook/webhook.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-user-settings',
  standalone: true,
  imports: [LucideAngularModule, FormsModule, RouterLink, MarkdownPipe],
  templateUrl: './user-settings.page.html',
  styleUrl: './user-settings.page.css',
})
export class UserSettingsPage {
  private readonly sshKeyService = inject(SshKeyService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);
  private readonly userService = inject(UserService);
  private readonly userWebhookService = inject(UserWebhookService);
  readonly tokenService = inject(TokenService);

  readonly activeTab = signal<'profile' | 'security' | 'ssh' | 'webhooks' | 'danger'>('profile');

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

  constructor() {
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

  setTab(t: 'profile' | 'security' | 'ssh' | 'webhooks' | 'danger'): void {
    this.activeTab.set(t);
    if (t === 'ssh') this.loadSshKeys();
    if (t === 'webhooks') void this.loadWebhooks();
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
      const msg = err instanceof Error ? err.message : 'Failed to update password';
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
}
