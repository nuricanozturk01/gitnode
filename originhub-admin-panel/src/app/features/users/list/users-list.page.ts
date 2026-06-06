import { Component, ChangeDetectionStrategy, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AdminUserService } from '../../../core/admin/admin-user.service';
import { ADMIN_PAGE_SIZE } from '../../../core/organization/organization.models';
import type { AdminUser } from '../../../core/admin/admin.models';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-users-list',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, PaginationComponent],
  templateUrl: './users-list.page.html',
})
export class UsersListPage implements OnInit {
  private readonly adminUserService = inject(AdminUserService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly users = signal<AdminUser[]>([]);
  readonly search = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly togglingId = signal<string | null>(null);

  readonly pageSize = ADMIN_PAGE_SIZE;

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    void this.load();
  }

  onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.search.set(value);
    this.page.set(0);

    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }

    this.searchTimer = setTimeout(() => {
      void this.load();
    }, 300);
  }

  async onPageChange(nextPage: number): Promise<void> {
    this.page.set(nextPage);
    await this.load();
  }

  formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  }

  async toggleEnabled(user: AdminUser): Promise<void> {
    if (this.togglingId()) return;

    if (user.enabled) {
      const confirmed = await this.confirmModal.confirm(
        'Disable user',
        `Disable "${user.username}"? They will not be able to sign in.`,
        { confirmLabel: 'Disable', variant: 'danger' },
      );
      if (!confirmed) return;
    }

    this.togglingId.set(user.id);
    try {
      const updated = await this.adminUserService.setEnabled(user.id, !user.enabled);
      this.users.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
      this.toast.success(updated.enabled ? 'User enabled' : 'User disabled');
    } catch {
      this.toast.error('Failed to update user');
    } finally {
      this.togglingId.set(null);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const res = await this.adminUserService.list({
        page: this.page(),
        size: this.pageSize,
        q: this.search().trim() || undefined,
      });
      this.users.set(res.content);
      this.totalPages.set(res.totalPages);
      this.totalElements.set(res.totalElements);
    } catch {
      this.toast.error('Failed to load users');
    } finally {
      this.loading.set(false);
    }
  }
}
