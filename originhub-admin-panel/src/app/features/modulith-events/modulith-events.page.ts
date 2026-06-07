import { Component, ChangeDetectionStrategy, computed, HostListener, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AdminModulithEventService } from '../../core/admin/admin-modulith-event.service';
import type {
  AuditPeriodPreset,
  ModulithEventLifecycleFilter,
  ModulithEventSummary,
} from '../../core/admin/admin.models';
import { AUDIT_PAGE_SIZE } from '../../core/organization/organization.models';
import { ToastService } from '../../core/toast/toast.service';
import { copyTextToClipboard } from '../../shared/utils/clipboard.util';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-modulith-events-page',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, PaginationComponent],
  templateUrl: './modulith-events.page.html',
  styleUrl: './modulith-events.page.css',
})
export class ModulithEventsPage implements OnInit {
  private readonly modulithEventService = inject(AdminModulithEventService);
  private readonly toast = inject(ToastService);

  readonly initialLoading = signal(true);
  readonly tableLoading = signal(false);
  readonly refreshing = signal(false);
  readonly filtersExpanded = signal(false);
  readonly detailOpen = signal(false);
  readonly detailLoading = signal(false);
  readonly selectedEntry = signal<ModulithEventSummary | null>(null);
  readonly detailPayload = signal<string | null>(null);
  readonly entries = signal<ModulithEventSummary[]>([]);
  readonly available = signal(false);
  readonly availabilityMessage = signal<string | null>(null);
  readonly eventTypeOptions = signal<string[]>([]);
  readonly listenerOptions = signal<string[]>([]);
  readonly statusOptions = signal<string[]>([]);
  readonly filtersTruncated = signal(false);

  readonly search = signal('');
  readonly eventType = signal('');
  readonly listenerId = signal('');
  readonly status = signal('');
  readonly lifecycle = signal<ModulithEventLifecycleFilter>('ALL');
  readonly period = signal<AuditPeriodPreset>('all');
  readonly from = signal('');
  readonly to = signal('');

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly pageSize = AUDIT_PAGE_SIZE;

  readonly lifecycleOptions: { value: ModulithEventLifecycleFilter; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'IN_PROGRESS', label: 'In progress' },
    { value: 'INCOMPLETE', label: 'Incomplete' },
    { value: 'FAILED', label: 'Failed' },
  ];

  readonly periodPresets: { value: AuditPeriodPreset; label: string }[] = [
    { value: 'all', label: 'All' },
    { value: '24h', label: '24h' },
    { value: '7d', label: '7d' },
    { value: '30d', label: '30d' },
  ];

  readonly resultsSummary = computed(() => {
    if (!this.available()) return this.availabilityMessage() ?? 'Modulith event viewer is disabled';
    const total = this.totalElements();
    if (total === 0) return 'No Modulith events found';
    return `${total} events · page ${this.page() + 1}/${Math.max(this.totalPages(), 1)}`;
  });

  private filterTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    void this.loadFilters();
    void this.load();
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.detailOpen()) {
      event.preventDefault();
      this.closeDetail();
    }
  }

  toggleFilters(): void {
    this.filtersExpanded.update((open) => !open);
  }

  async openDetail(entry: ModulithEventSummary): Promise<void> {
    this.selectedEntry.set(entry);
    this.detailOpen.set(true);
    this.detailPayload.set(entry.eventPreview);
    this.detailLoading.set(true);
    try {
      const detail = await this.modulithEventService.detail(entry.id);
      this.detailPayload.set(detail.serializedEvent);
    } catch {
      this.toast.error('Failed to load event payload');
    } finally {
      this.detailLoading.set(false);
    }
  }

  closeDetail(): void {
    this.detailOpen.set(false);
    this.selectedEntry.set(null);
    this.detailPayload.set(null);
  }

  async refresh(): Promise<void> {
    this.refreshing.set(true);
    try {
      await this.load();
      this.toast.success('Modulith events updated');
    } finally {
      this.refreshing.set(false);
    }
  }

  onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
    this.scheduleReload(300);
  }

  onEventTypeChange(event: Event): void {
    this.eventType.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
    void this.load();
  }

  onListenerChange(event: Event): void {
    this.listenerId.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
    void this.load();
  }

  onStatusChange(event: Event): void {
    this.status.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
    void this.load();
  }

  onLifecycleChange(value: ModulithEventLifecycleFilter): void {
    this.lifecycle.set(value);
    this.page.set(0);
    void this.load();
  }

  async onPageChange(nextPage: number): Promise<void> {
    this.page.set(nextPage);
    this.closeDetail();
    await this.load();
  }

  statusBadgeClass(status: string | null): string {
    switch (status?.toUpperCase()) {
      case 'FAILED':
        return 'badge-pill badge-pill--error audit-action';
      case 'COMPLETED':
        return 'badge-pill badge-pill--success audit-action';
      case 'PROCESSING':
        return 'badge-pill badge-pill--warning audit-action';
      default:
        return 'badge-pill badge-pill--neutral audit-action';
    }
  }

  formatDateTime(value: string | null): string {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString();
  }

  summaryLine(entry: ModulithEventSummary): string {
    return entry.eventPreview ?? entry.eventType;
  }

  async copyText(label: string, value: string | null): Promise<void> {
    if (!value) return;
    try {
      await copyTextToClipboard(value);
      this.toast.success(`${label} copied`);
    } catch {
      this.toast.error('Could not copy to clipboard');
    }
  }

  private scheduleReload(delayMs: number): void {
    this.page.set(0);
    if (this.filterTimer) clearTimeout(this.filterTimer);
    this.filterTimer = setTimeout(() => void this.load(), delayMs);
  }

  private async loadFilters(): Promise<void> {
    try {
      const res = await this.modulithEventService.filters();
      this.eventTypeOptions.set(res.eventTypes);
      this.listenerOptions.set(res.listenerIds);
      this.statusOptions.set(res.statuses);
      this.filtersTruncated.set(res.truncated);
    } catch {
      // Filters are optional when viewer is disabled.
    }
  }

  private async load(): Promise<void> {
    const isFirstLoad = this.initialLoading();
    if (!isFirstLoad) this.tableLoading.set(true);

    try {
      const status = await this.modulithEventService.status();
      this.available.set(status.available);
      this.availabilityMessage.set(status.message);

      if (!status.available) {
        this.entries.set([]);
        this.totalPages.set(0);
        this.totalElements.set(0);
        return;
      }

      const res = await this.modulithEventService.list({
        page: this.page(),
        size: this.pageSize,
        q: this.search().trim() || undefined,
        eventType: this.eventType() || undefined,
        listenerId: this.listenerId() || undefined,
        status: this.status() || undefined,
        lifecycle: this.lifecycle(),
        from: this.resolveFrom(),
        to: this.resolveTo(),
      });

      this.entries.set(res.content);
      this.totalPages.set(res.totalPages);
      this.totalElements.set(res.totalElements);
    } catch {
      this.toast.error('Failed to load Modulith events');
    } finally {
      this.initialLoading.set(false);
      this.tableLoading.set(false);
    }
  }

  private resolveFrom(): string | undefined {
    const customFrom = this.from().trim();
    if (customFrom) return new Date(customFrom).toISOString();
    const preset = this.period();
    if (preset === 'all') return undefined;
    const hours = preset === '24h' ? 24 : preset === '7d' ? 168 : 720;
    return new Date(Date.now() - hours * 3_600_000).toISOString();
  }

  private resolveTo(): string | undefined {
    const customTo = this.to().trim();
    if (!customTo) return undefined;
    return new Date(customTo).toISOString();
  }
}
