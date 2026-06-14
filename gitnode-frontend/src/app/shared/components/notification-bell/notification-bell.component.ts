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
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { NotificationService } from '../../../core/notification/notification.service';
import type { NotificationDto } from '../../../domain/notification/notification.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-notification-bell',
  standalone: true,
  imports: [LucideAngularModule, DatePipe, RouterLink],
  templateUrl: './notification-bell.component.html',
})
export class NotificationBellComponent {
  private readonly router = inject(Router);
  readonly notificationService = inject(NotificationService);

  readonly open = signal(false);
  readonly loading = signal(false);
  readonly loadingMore = signal(false);
  readonly deletingId = signal<string | null>(null);

  readonly unreadCount = this.notificationService.unreadCount;
  readonly notifications = this.notificationService.notifications;
  readonly hasMore = this.notificationService.hasMore;
  readonly hasUnread = computed(() => this.unreadCount() > 0);
  readonly hasAny = computed(() => this.notifications().length > 0);

  async toggle(): Promise<void> {
    const nowOpen = !this.open();
    this.open.set(nowOpen);
    if (nowOpen && this.notifications().length === 0) {
      await this.load();
    }
  }

  close(): void {
    this.open.set(false);
  }

  async load(): Promise<void> {
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

  async clickNotification(notification: NotificationDto): Promise<void> {
    if (!notification.read) {
      await this.notificationService.markRead(notification.id);
    }
    if (notification.link) {
      this.close();
      void this.router.navigateByUrl(notification.link);
    }
  }

  async deleteNotification(notification: NotificationDto, event: Event): Promise<void> {
    event.stopPropagation();
    this.deletingId.set(notification.id);
    try {
      await this.notificationService.deleteNotification(notification.id);
    } finally {
      this.deletingId.set(null);
    }
  }

  async markAllRead(): Promise<void> {
    await this.notificationService.markAllRead();
  }

  async deleteAll(): Promise<void> {
    await this.notificationService.deleteAll();
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
