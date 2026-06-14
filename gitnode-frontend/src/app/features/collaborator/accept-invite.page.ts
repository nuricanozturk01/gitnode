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

import { Component, ChangeDetectionStrategy, inject, signal, OnInit } from '@angular/core';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { CollaboratorService } from '../../core/collaborator/collaborator.service';
import { TokenService } from '../../core/auth/services/token.service';
import { ToastService } from '../../core/toast/toast.service';
import type { InvitationTokenInfo } from '../../domain/collaborator/collaborator.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-accept-invite',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, DatePipe],
  template: `
    <div class="bg-base-200 flex min-h-screen items-center justify-center px-4 py-12">
      <div class="card bg-base-100 border-base-300 w-full max-w-md border shadow-md">
        <div class="card-body gap-4">
          @if (loading()) {
            <div class="flex justify-center py-8">
              <span class="loading loading-spinner loading-lg text-primary"></span>
            </div>
          } @else if (expired()) {
            <div class="flex flex-col items-center gap-3 py-4 text-center">
              <lucide-icon name="xCircle" class="text-error size-10"></lucide-icon>
              <h1 class="text-lg font-semibold">Invitation link expired</h1>
              <p class="text-base-content/60 text-sm">
                This invite link has expired. Ask the repo owner to generate a new one.
              </p>
              <a routerLink="/" class="btn btn-primary btn-sm mt-2">Go home</a>
            </div>
          } @else if (notFound()) {
            <div class="flex flex-col items-center gap-3 py-4 text-center">
              <lucide-icon name="alertTriangle" class="text-warning size-10"></lucide-icon>
              <h1 class="text-lg font-semibold">Invitation not found</h1>
              <p class="text-base-content/60 text-sm">This invite link is invalid or has already been used.</p>
              <a routerLink="/" class="btn btn-primary btn-sm mt-2">Go home</a>
            </div>
          } @else if (accepted()) {
            <div class="flex flex-col items-center gap-3 py-4 text-center">
              <lucide-icon name="checkCircle" class="text-success size-10"></lucide-icon>
              <h1 class="text-lg font-semibold">You're now a collaborator!</h1>
              <p class="text-base-content/60 text-sm">
                You have access to
                <strong>{{ invitation()?.repoOwner }}/{{ invitation()?.repoName }}</strong
                >.
              </p>
              <a
                [routerLink]="['/', invitation()?.repoOwner, invitation()?.repoName]"
                class="btn btn-primary btn-sm mt-2"
                >Open repository</a
              >
            </div>
          } @else if (declined()) {
            <div class="flex flex-col items-center gap-3 py-4 text-center">
              <lucide-icon name="xCircle" class="text-base-content/40 size-10"></lucide-icon>
              <h1 class="text-lg font-semibold">Invitation declined</h1>
              <p class="text-base-content/60 text-sm">
                You declined the invitation to
                <strong>{{ invitation()?.repoOwner }}/{{ invitation()?.repoName }}</strong
                >.
              </p>
              <a routerLink="/" class="btn btn-ghost btn-sm mt-2">Go home</a>
            </div>
          } @else if (invitation(); as inv) {
            <div class="flex flex-col gap-1">
              <lucide-icon name="userPlus" class="text-primary size-8"></lucide-icon>
              <h1 class="mt-2 text-xl font-bold">Repository invitation</h1>
              <p class="text-base-content/65 text-sm">
                <strong>{{ inv.invitedBy }}</strong> has invited you to collaborate on
                <strong>{{ inv.repoOwner }}/{{ inv.repoName }}</strong
                >.
              </p>
            </div>
            <div class="flex flex-wrap gap-1">
              @for (perm of inv.permissions; track perm) {
                <span class="badge-pill badge-pill--permission text-xs">{{ perm }}</span>
              }
            </div>
            <p class="text-base-content/50 text-xs">Expires {{ inv.expiresAt | date: 'medium' }}</p>
            @if (!isLoggedIn()) {
              <div class="alert alert-warning text-sm">You must be signed in to accept this invitation.</div>
              <a [routerLink]="['/login']" [queryParams]="{ redirect: currentUrl() }" class="btn btn-primary w-full"
                >Sign in to accept</a
              >
            } @else {
              <div class="flex gap-2">
                <button type="button" class="btn btn-success flex-1" [disabled]="responding()" (click)="accept()">
                  @if (responding() === 'accept') {
                    <span class="loading loading-spinner loading-xs"></span>
                  }
                  Accept
                </button>
                <button
                  type="button"
                  class="btn btn-error btn-outline flex-1"
                  [disabled]="!!responding()"
                  (click)="decline()"
                >
                  @if (responding() === 'decline') {
                    <span class="loading loading-spinner loading-xs"></span>
                  }
                  Decline
                </button>
              </div>
            }
          }
        </div>
      </div>
    </div>
  `,
})
export class AcceptInvitePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly collaboratorService = inject(CollaboratorService);
  private readonly tokenService = inject(TokenService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly expired = signal(false);
  readonly accepted = signal(false);
  readonly declined = signal(false);
  readonly responding = signal<'accept' | 'decline' | null>(null);
  readonly invitation = signal<InvitationTokenInfo | null>(null);
  readonly isLoggedIn = signal(false);
  readonly currentUrl = signal('');
  private token = '';

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token') ?? '';
    this.isLoggedIn.set(!!this.tokenService.getAccessToken());
    this.currentUrl.set(window.location.pathname);
    void this.loadInvitation();
  }

  private async loadInvitation(): Promise<void> {
    if (!this.token) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }
    try {
      const inv = await this.collaboratorService.getInvitationByToken(this.token);
      this.invitation.set(inv);
    } catch (err: unknown) {
      const status = (err as { status?: number }).status;
      if (status === 400) {
        this.expired.set(true);
      } else {
        this.notFound.set(true);
      }
    } finally {
      this.loading.set(false);
    }
  }

  async accept(): Promise<void> {
    if (this.responding()) return;
    this.responding.set('accept');
    try {
      await this.collaboratorService.acceptViaToken(this.token);
      this.accepted.set(true);
    } catch (err: unknown) {
      const status = (err as { status?: number }).status;
      if (status === 400) {
        this.expired.set(true);
        this.toast.error('Invitation link has expired');
      } else if (status === 403) {
        this.toast.error('This invitation is not for your account');
      } else {
        this.toast.error('Failed to accept invitation');
      }
    } finally {
      this.responding.set(null);
    }
  }

  async decline(): Promise<void> {
    if (this.responding()) return;
    this.responding.set('decline');
    try {
      await this.collaboratorService.declineViaToken(this.token);
      this.declined.set(true);
    } catch (err: unknown) {
      const status = (err as { status?: number }).status;
      if (status === 403) {
        this.toast.error('This invitation is not for your account');
      } else {
        this.toast.error('Failed to decline invitation');
      }
    } finally {
      this.responding.set(null);
    }
  }
}
