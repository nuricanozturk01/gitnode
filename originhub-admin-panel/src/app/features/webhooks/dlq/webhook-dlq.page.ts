import { Component, ChangeDetectionStrategy, inject, OnInit, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { AdminWebhookDlqService } from '../../../core/admin/admin-webhook-dlq.service';
import type { WebhookDlqEntry } from '../../../core/admin/admin.models';
import { ADMIN_PAGE_SIZE } from '../../../core/organization/organization.models';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import { apiErrorMessage } from '../../../shared/utils/api-error';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-webhook-dlq',
  standalone: true,
  imports: [LucideAngularModule, PaginationComponent],
  templateUrl: './webhook-dlq.page.html',
})
export class WebhookDlqPage implements OnInit {
  private readonly dlqService = inject(AdminWebhookDlqService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly actingId = signal<string | null>(null);
  readonly entries = signal<WebhookDlqEntry[]>([]);
  readonly pendingCount = signal(0);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly pageSize = ADMIN_PAGE_SIZE;

  ngOnInit(): void {
    void this.load();
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

  async onPageChange(nextPage: number): Promise<void> {
    this.page.set(nextPage);
    await this.loadEntries();
  }

  async retry(entry: WebhookDlqEntry): Promise<void> {
    if (this.actingId()) return;
    this.actingId.set(entry.id);
    try {
      await this.dlqService.retry(entry.id);
      this.toast.success('Webhook redelivered');
      await this.load();
    } catch (e) {
      this.toast.error(apiErrorMessage(e, 'Retry failed'));
    } finally {
      this.actingId.set(null);
    }
  }

  async dismiss(entry: WebhookDlqEntry): Promise<void> {
    const confirmed = await this.confirmModal.confirm(
      'Dismiss dead letter',
      'Remove this failed delivery from the queue without retrying?',
      { confirmLabel: 'Dismiss', variant: 'danger' },
    );
    if (!confirmed) return;

    if (this.actingId()) return;
    this.actingId.set(entry.id);
    try {
      await this.dlqService.dismiss(entry.id);
      this.toast.success('Dead letter dismissed');
      await this.load();
    } catch (e) {
      this.toast.error(apiErrorMessage(e, 'Dismiss failed'));
    } finally {
      this.actingId.set(null);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const summary = await this.dlqService.summary();
      await this.loadEntries();
      this.pendingCount.set(summary.pending);
    } catch {
      this.toast.error('Failed to load webhook dead letters');
    } finally {
      this.loading.set(false);
    }
  }

  private async loadEntries(): Promise<void> {
    const res = await this.dlqService.list({ page: this.page(), size: this.pageSize });
    this.entries.set(res.content);
    this.totalPages.set(res.totalPages);
    this.totalElements.set(res.totalElements);
  }
}
