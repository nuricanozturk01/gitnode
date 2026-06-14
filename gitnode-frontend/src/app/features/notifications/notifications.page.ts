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
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { NotificationService } from '../../core/notification/notification.service';
import { ConfirmModalService } from '../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../core/toast/toast.service';
import type { NotificationDto } from '../../domain/notification/notification.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-notifications',
  standalone: true,
  imports: [LucideAngularModule, DatePipe, RouterLink],
  template: `
    <div class="container mx-auto max-w-3xl px-4 py-8">
      <!-- Header -->
      <div class="mb-6 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <lucide-icon name="bell" class="text-primary size-6"></lucide-icon>
          <h1 class="text-base-content text-xl font-bold">Notifications</h1>
          @if (unreadCount() > 0) {
            <span class="badge-pill badge-pill--error">{{ unreadCount() }}</span>
          }
        </div>
        <div class="flex items-center gap-2">
          @if (unreadCount() > 0) {
            <button
              type="button"
              class="btn btn-ghost btn-sm gap-1.5"
              [disabled]="markingAll()"
              (click)="markAllRead()"
            >
              @if (markingAll()) {
                <span class="loading loading-spinner loading-xs"></span>
              } @else {
                <lucide-icon name="check-check" class="size-4"></lucide-icon>
              }
              Mark all read
            </button>
          }
          @if (notifications().length > 0) {
            <button
              type="button"
              class="btn btn-ghost btn-sm text-error hover:bg-error/10"
              [disabled]="clearingAll()"
              (click)="clearAll()"
            >
              @if (clearingAll()) {
                <span class="loading loading-spinner loading-xs"></span>
              } @else {
                <lucide-icon name="trash" class="size-4"></lucide-icon>
              }
              Clear all
            </button>
          }
        </div>
      </div>

      <!-- List -->
      @if (loading()) {
        <div class="flex justify-center py-16">
          <span class="loading loading-spinner loading-lg text-primary"></span>
        </div>
      } @else if (notifications().length === 0) {
        <div class="flex flex-col items-center gap-3 py-20 text-center">
          <lucide-icon name="bell-off" class="text-base-content/20 size-12"></lucide-icon>
          <p class="text-base-content/50 font-medium">No notifications yet</p>
          <p class="text-base-content/35 text-sm">We'll let you know when something happens.</p>
        </div>
      } @else {
        <div class="divide-base-200 bg-base-100 border-base-300 divide-y overflow-hidden rounded-xl border shadow-sm">
          @for (n of notifications(); track n.id) {
            <div
              class="group hover:bg-base-200 relative flex items-start gap-3 px-4 py-4 transition-colors"
              [class.bg-primary/5]="!n.read"
            >
              <!-- Icon -->
              <div class="bg-base-200 mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full">
                <lucide-icon
                  [name]="notificationIcon(n.type)"
                  class="size-4"
                  [class]="notificationIconClass(n.type)"
                ></lucide-icon>
              </div>

              <!-- Content -->
              <button type="button" class="min-w-0 flex-1 text-left" (click)="clickNotification(n)">
                <p
                  class="text-base-content text-sm leading-snug"
                  [class.font-semibold]="!n.read"
                  [class.font-medium]="n.read"
                >
                  {{ n.title }}
                </p>
                @if (n.body) {
                  <p class="text-base-content/55 mt-1 line-clamp-3 text-xs leading-relaxed whitespace-pre-wrap">
                    {{ n.body }}
                  </p>
                }
                <p class="text-base-content/35 mt-1.5 text-[11px]">
                  {{ n.createdAt | date: 'MMM d, y, h:mm a' }}
                </p>
              </button>

              <!-- Unread dot -->
              @if (!n.read) {
                <span class="bg-primary mt-2 size-2 shrink-0 rounded-full"></span>
              }

              <!-- Delete button -->
              <button
                type="button"
                class="btn btn-ghost btn-xs btn-square text-error hover:bg-error/10 absolute top-3 right-3 opacity-0 transition-opacity group-hover:opacity-100"
                title="Remove"
                [disabled]="deletingId() === n.id"
                (click)="deleteOne(n)"
              >
                @if (deletingId() === n.id) {
                  <span class="loading loading-spinner loading-xs"></span>
                } @else {
                  <lucide-icon name="x" class="size-3.5"></lucide-icon>
                }
              </button>
            </div>
          }
        </div>

        <!-- Load more -->
        @if (hasMore()) {
          <div class="mt-4 flex justify-center">
            <button type="button" class="btn btn-ghost btn-sm" [disabled]="loadingMore()" (click)="loadMore()">
              @if (loadingMore()) {
                <span class="loading loading-spinner loading-xs"></span>
              }
              Load more
            </button>
          </div>
        }
      }
    </div>
  `,
})
export class NotificationsPage implements OnInit {
  private readonly router = inject(Router);
  private readonly notificationService = inject(NotificationService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly loadingMore = signal(false);
  readonly markingAll = signal(false);
  readonly clearingAll = signal(false);
  readonly deletingId = signal<string | null>(null);

  readonly notifications = this.notificationService.notifications;
  readonly hasMore = this.notificationService.hasMore;
  readonly unreadCount = this.notificationService.unreadCount;

  async ngOnInit(): Promise<void> {
    this.loading.set(true);
    try {
      await this.notificationService.loadNotifications(0);
    } finally {
      this.loading.set(false);
    }
  }

  async loadMore(): Promise<void> {
    this.loadingMore.set(true);
    try {
      await this.notificationService.loadMore();
    } finally {
      this.loadingMore.set(false);
    }
  }

  async clickNotification(n: NotificationDto): Promise<void> {
    if (!n.read) {
      await this.notificationService.markRead(n.id);
    }
    if (n.link) {
      void this.router.navigateByUrl(n.link);
    }
  }

  async markAllRead(): Promise<void> {
    this.markingAll.set(true);
    try {
      await this.notificationService.markAllRead();
    } finally {
      this.markingAll.set(false);
    }
  }

  async clearAll(): Promise<void> {
    const confirmed = await this.confirmModal.confirm(
      'Clear all notifications',
      'This will permanently delete all your notifications.',
      { confirmLabel: 'Clear all', variant: 'danger' },
    );
    if (!confirmed) return;
    this.clearingAll.set(true);
    try {
      await this.notificationService.deleteAll();
    } catch {
      this.toast.error('Could not clear notifications');
    } finally {
      this.clearingAll.set(false);
    }
  }

  async deleteOne(n: NotificationDto): Promise<void> {
    this.deletingId.set(n.id);
    try {
      await this.notificationService.deleteNotification(n.id);
    } catch {
      this.toast.error('Could not delete notification');
    } finally {
      this.deletingId.set(null);
    }
  }

  notificationIcon(type: NotificationDto['type']): string {
    switch (type) {
      case 'ISSUE_COMMENT':
        return 'message-circle';
      case 'PR_COMMENT':
        return 'git-pull-request';
      case 'PR_MERGED':
        return 'git-merge';
      case 'PR_CLOSED':
        return 'git-pull-request-closed';
      case 'AI_CODE_REVIEW_COMPLETED':
      case 'AI_ANALYSIS_COMPLETED':
        return 'sparkles';
      case 'AI_CODE_REVIEW_FAILED':
      case 'AI_ANALYSIS_FAILED':
        return 'alert-circle';
      case 'COLLABORATOR_INVITED':
        return 'user-plus';
      default:
        return 'bell';
    }
  }

  notificationIconClass(type: NotificationDto['type']): string {
    switch (type) {
      case 'PR_MERGED':
      case 'AI_CODE_REVIEW_COMPLETED':
      case 'AI_ANALYSIS_COMPLETED':
        return 'text-success';
      case 'AI_CODE_REVIEW_FAILED':
      case 'AI_ANALYSIS_FAILED':
      case 'PR_CLOSED':
        return 'text-error';
      case 'COLLABORATOR_INVITED':
        return 'text-primary';
      default:
        return 'text-base-content/50';
    }
  }
}
