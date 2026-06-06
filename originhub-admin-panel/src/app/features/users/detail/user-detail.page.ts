import { Component, ChangeDetectionStrategy, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AdminUserService } from '../../../core/admin/admin-user.service';
import type { AdminUserDetail } from '../../../core/admin/admin.models';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import { apiErrorMessage } from '../../../shared/utils/api-error';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-user-detail',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './user-detail.page.html',
})
export class UserDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly adminUserService = inject(AdminUserService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly toggling = signal(false);
  readonly user = signal<AdminUserDetail | null>(null);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.load();
  }

  formatDateTime(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  auditLogsQuery(): { actor: string } | null {
    const username = this.user()?.username;
    return username ? { actor: username } : null;
  }

  async toggleEnabled(): Promise<void> {
    const current = this.user();
    if (!current || this.toggling()) return;

    if (current.enabled) {
      const confirmed = await this.confirmModal.confirm(
        'Disable user',
        `Disable "${current.username}"? They will not be able to sign in.`,
        { confirmLabel: 'Disable', variant: 'danger' },
      );
      if (!confirmed) return;
    }

    this.toggling.set(true);
    try {
      const updated = await this.adminUserService.setEnabled(current.id, !current.enabled);
      this.user.update((value) =>
        value
          ? {
              ...value,
              enabled: updated.enabled,
            }
          : value,
      );
      this.toast.success(updated.enabled ? 'User enabled' : 'User disabled');
    } catch (e) {
      this.toast.error(apiErrorMessage(e, 'Failed to update user'));
    } finally {
      this.toggling.set(false);
    }
  }

  private async load(): Promise<void> {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('User not found');
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    try {
      this.user.set(await this.adminUserService.get(id));
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Failed to load user'));
    } finally {
      this.loading.set(false);
    }
  }
}
