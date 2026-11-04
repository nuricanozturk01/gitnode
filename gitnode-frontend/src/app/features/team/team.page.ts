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

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { ConfirmModalService } from '../../core/confirm-modal/confirm-modal.service';
import { CollaboratorService } from '../../core/collaborator/collaborator.service';
import { TeamService } from '../../core/team/team.service';
import { ToastService } from '../../core/toast/toast.service';
import {
  TEAM_ROLES,
  type TeamMemberStatus,
  type TeamPage as TeamMemberPage,
  type TeamRole,
} from '../../domain/team/team.model';

interface UserSuggestion {
  username: string;
  displayName: string | null;
  avatarUrl: string;
}

const PAGE_SIZE = 20;

@Component({
  selector: 'app-team-page',
  standalone: true,
  imports: [FormsModule, NgTemplateOutlet, LucideAngularModule],
  templateUrl: './team.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TeamPage {
  private readonly teamService = inject(TeamService);
  private readonly collaboratorService = inject(CollaboratorService);
  private readonly toast = inject(ToastService);
  private readonly confirmModal = inject(ConfirmModalService);

  readonly roles = TEAM_ROLES;
  readonly loading = signal(true);

  readonly invitationPage = signal<TeamMemberPage | null>(null);
  readonly invitationIndex = signal(0);
  readonly invitations = computed(() => this.invitationPage()?.content ?? []);
  readonly invitationPages = computed(() => this.invitationPage()?.totalPages ?? 0);

  readonly membershipPage = signal<TeamMemberPage | null>(null);
  readonly membershipIndex = signal(0);
  readonly memberships = computed(() => this.membershipPage()?.content ?? []);
  readonly membershipPages = computed(() => this.membershipPage()?.totalPages ?? 0);

  readonly statusFilter = signal<TeamMemberStatus>('ACCEPTED');
  readonly memberPage = signal<TeamMemberPage | null>(null);
  readonly memberIndex = signal(0);
  readonly members = computed(() => this.memberPage()?.content ?? []);
  readonly memberPages = computed(() => this.memberPage()?.totalPages ?? 0);

  readonly inviteUsername = signal('');
  readonly inviteRole = signal<TeamRole>('DEVELOPER');
  readonly inviting = signal(false);
  readonly userSuggestions = signal<UserSuggestion[]>([]);
  readonly showSuggestions = signal(false);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    void this.init();
  }

  async init(): Promise<void> {
    await Promise.all([this.loadInvitations(0), this.loadMemberships(0), this.loadMembers(0)]);
    this.loading.set(false);
  }

  async loadInvitations(page: number): Promise<void> {
    try {
      this.invitationPage.set(await this.teamService.myInvitations(page, PAGE_SIZE));
      this.invitationIndex.set(page);
    } catch {
      this.toast.error('Failed to load invitations');
    }
  }

  async loadMemberships(page: number): Promise<void> {
    try {
      this.membershipPage.set(await this.teamService.memberships(page, PAGE_SIZE));
      this.membershipIndex.set(page);
    } catch {
      this.toast.error('Failed to load teams');
    }
  }

  async loadMembers(page: number): Promise<void> {
    try {
      this.memberPage.set(await this.teamService.members(this.statusFilter(), page, PAGE_SIZE));
      this.memberIndex.set(page);
    } catch {
      this.toast.error('Failed to load members');
    }
  }

  setStatusFilter(status: TeamMemberStatus): void {
    this.statusFilter.set(status);
    void this.loadMembers(0);
  }

  async accept(id: string): Promise<void> {
    try {
      await this.teamService.accept(id);
      this.toast.success('Invitation accepted');
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to accept invitation');
    }
    await this.refreshAfterInvitation();
  }

  async decline(id: string): Promise<void> {
    try {
      await this.teamService.decline(id);
      this.toast.success('Invitation declined');
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to decline invitation');
    }
    await this.refreshAfterInvitation();
  }

  private async refreshAfterInvitation(): Promise<void> {
    const index = this.invitationIndex();
    await Promise.all([
      this.teamService.loadMe(),
      this.loadInvitations(this.invitations().length === 1 && index > 0 ? index - 1 : index),
      this.loadMemberships(0),
    ]);
  }

  async invite(): Promise<void> {
    const username = this.inviteUsername().trim();
    if (!username || this.inviting()) return;
    this.inviting.set(true);
    try {
      await this.teamService.invite(username, this.inviteRole());
      this.toast.success(`Invitation sent to ${username}`);
      this.inviteUsername.set('');
      this.userSuggestions.set([]);
      await this.loadMembers(0);
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to send invitation');
    } finally {
      this.inviting.set(false);
    }
  }

  async changeRole(id: string, role: TeamRole): Promise<void> {
    try {
      await this.teamService.changeRole(id, role);
      this.toast.success('Role updated');
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to update role');
    }
    await this.loadMembers(this.memberIndex());
  }

  async remove(id: string, username: string): Promise<void> {
    const ok = await this.confirmModal.confirm('Remove member', `Remove ${username} from your team?`, {
      confirmLabel: 'Remove',
      variant: 'danger',
    });
    if (!ok) return;
    await this.runAndReloadMembers(() => this.teamService.remove(id), 'Member removed', 'Failed to remove member');
  }

  async cancelInvitation(id: string, username: string): Promise<void> {
    const ok = await this.confirmModal.confirm('Cancel invitation', `Cancel the invitation for ${username}?`, {
      confirmLabel: 'Cancel invitation',
      variant: 'danger',
    });
    if (!ok) return;
    await this.runAndReloadMembers(
      () => this.teamService.cancelInvitation(id),
      'Invitation cancelled',
      'Failed to cancel',
    );
  }

  async leave(id: string, owner: string): Promise<void> {
    const ok = await this.confirmModal.confirm('Leave team', `You will lose access to the repositories of ${owner}.`, {
      confirmLabel: 'Leave',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await this.teamService.leave(id);
      this.toast.success(`You left the team of ${owner}`);
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to leave team');
    }
    const index = this.membershipIndex();
    await this.loadMemberships(this.memberships().length === 1 && index > 0 ? index - 1 : index);
  }

  private async runAndReloadMembers(action: () => Promise<void>, success: string, failure: string): Promise<void> {
    try {
      await action();
      this.toast.success(success);
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : failure);
    }
    const index = this.memberIndex();
    await this.loadMembers(this.members().length === 1 && index > 0 ? index - 1 : index);
  }

  onInviteUsernameInput(value: string): void {
    this.inviteUsername.set(value);
    this.showSuggestions.set(false);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    if (value.trim().length < 3) {
      this.userSuggestions.set([]);
      return;
    }
    this.searchTimer = setTimeout(() => void this.fetchSuggestions(value.trim()), 300);
  }

  private async fetchSuggestions(q: string): Promise<void> {
    try {
      const results = await this.collaboratorService.searchUsers(q);
      this.userSuggestions.set(results);
      this.showSuggestions.set(results.length > 0);
    } catch {
      this.userSuggestions.set([]);
    }
  }

  selectSuggestion(username: string): void {
    this.inviteUsername.set(username);
    this.userSuggestions.set([]);
    this.showSuggestions.set(false);
  }

  hideSuggestions(): void {
    setTimeout(() => this.showSuggestions.set(false), 150);
  }
}
