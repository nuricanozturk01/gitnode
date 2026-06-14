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

import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type {
  NotificationDto,
  NotificationPage,
  NotificationPreference,
  NotificationType,
} from '../../domain/notification/notification.model';
import { TokenService } from '../auth/services/token.service';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly tokenService = inject(TokenService);
  private readonly base = `${environment.apiUrl}/api/notifications`;

  readonly unreadCount = signal(0);
  readonly notifications = signal<NotificationDto[]>([]);
  readonly hasMore = signal(false);
  readonly currentPage = signal(0);

  private eventSource: EventSource | null = null;
  private readonly PAGE_SIZE = 20;

  connectSse(): void {
    if (this.eventSource) return;
    const token = this.tokenService.getAccessToken();
    if (!token || token.trim() === '') return;

    this.eventSource = new EventSource(`${this.base}/stream?token=${encodeURIComponent(token)}`);

    this.eventSource.addEventListener('notification', (ev: MessageEvent) => {
      try {
        const dto: NotificationDto = JSON.parse(ev.data as string);
        this.notifications.update((list) => [dto, ...list]);
        this.unreadCount.update((c) => c + 1);
      } catch {
        // ignore parse errors
      }
    });

    this.eventSource.onerror = () => {
      this.eventSource?.close();
      this.eventSource = null;
      setTimeout(() => this.connectSse(), 5000);
    };
  }

  disconnectSse(): void {
    this.eventSource?.close();
    this.eventSource = null;
  }

  async loadUnreadCount(): Promise<void> {
    try {
      const res = await firstValueFrom(this.http.get<{ count: number }>(`${this.base}/unread-count`));
      this.unreadCount.set(res.count);
    } catch {
      // ignore
    }
  }

  async loadNotifications(page = 0): Promise<void> {
    const params = new HttpParams().set('page', page).set('size', this.PAGE_SIZE);
    const res = await firstValueFrom(this.http.get<NotificationPage>(this.base, { params }));
    if (page === 0) {
      this.notifications.set(res.content);
    } else {
      this.notifications.update((list) => [...list, ...res.content]);
    }
    this.currentPage.set(page);
    this.hasMore.set(page + 1 < res.totalPages);
  }

  async loadMore(): Promise<void> {
    await this.loadNotifications(this.currentPage() + 1);
  }

  async markRead(id: string): Promise<void> {
    await firstValueFrom(this.http.put<void>(`${this.base}/${id}/read`, null));
    this.notifications.update((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)));
    this.unreadCount.update((c) => Math.max(0, c - 1));
  }

  async markAllRead(): Promise<void> {
    await firstValueFrom(this.http.put<void>(`${this.base}/read-all`, null));
    this.notifications.update((list) => list.map((n) => ({ ...n, read: true })));
    this.unreadCount.set(0);
  }

  async deleteNotification(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${this.base}/${id}`));
    const wasUnread = this.notifications().find((n) => n.id === id)?.read === false;
    this.notifications.update((list) => list.filter((n) => n.id !== id));
    if (wasUnread) {
      this.unreadCount.update((c) => Math.max(0, c - 1));
    }
  }

  async deleteAll(): Promise<void> {
    await firstValueFrom(this.http.delete<void>(this.base));
    this.notifications.set([]);
    this.unreadCount.set(0);
    this.hasMore.set(false);
    this.currentPage.set(0);
  }

  async getPreferences(): Promise<NotificationPreference[]> {
    return firstValueFrom(this.http.get<NotificationPreference[]>(`${this.base}/preferences`));
  }

  async setPreference(type: NotificationType, enabled: boolean): Promise<NotificationPreference> {
    return firstValueFrom(this.http.put<NotificationPreference>(`${this.base}/preferences/${type}`, { enabled }));
  }
}
