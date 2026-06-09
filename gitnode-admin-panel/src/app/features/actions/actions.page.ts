import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AdminActionsService, AdminActionsStats, AdminRunnerSummary } from '../../core/admin/admin-actions.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-actions-page',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './actions.page.html',
})
export class ActionsPage implements OnInit {
  private readonly actionsService = inject(AdminActionsService);

  readonly stats = signal<AdminActionsStats | null>(null);
  readonly runners = signal<AdminRunnerSummary[]>([]);
  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.load();
  }

  async refresh(): Promise<void> {
    this.refreshing.set(true);
    await this.load(false);
    this.refreshing.set(false);
  }

  private load(showFullSpinner = true): Promise<void> {
    if (showFullSpinner) {
      this.loading.set(true);
    }
    this.error.set(null);

    return new Promise((resolve) => {
      let pending = 2;
      let failed = false;

      const done = (): void => {
        pending -= 1;
        if (pending === 0) {
          if (failed) {
            this.error.set('Failed to load Actions data');
          }
          this.loading.set(false);
          resolve();
        }
      };

      this.actionsService.getStats().subscribe({
        next: (s) => this.stats.set(s),
        error: () => {
          failed = true;
          done();
        },
        complete: done,
      });

      this.actionsService.getRunners().subscribe({
        next: (r) => this.runners.set(r),
        error: () => {
          failed = true;
          done();
        },
        complete: done,
      });
    });
  }

  formatDateTime(value: string | null): string {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  statusBadgeClass(status: string): string {
    switch (status) {
      case 'ONLINE':
        return 'badge-pill badge-pill--success';
      case 'BUSY':
        return 'badge-pill badge-pill--warning';
      default:
        return 'badge-pill badge-pill--neutral';
    }
  }

  statusIconClass(status: string): string {
    switch (status) {
      case 'ONLINE':
        return 'text-success';
      case 'BUSY':
        return 'text-warning';
      default:
        return 'text-base-content/40';
    }
  }

  statusIconName(status: string): string {
    switch (status) {
      case 'ONLINE':
        return 'checkCircle';
      case 'BUSY':
        return 'loader2';
      default:
        return 'circleAlert';
    }
  }

  statusLabel(status: string): string {
    return status.charAt(0) + status.slice(1).toLowerCase();
  }
}
