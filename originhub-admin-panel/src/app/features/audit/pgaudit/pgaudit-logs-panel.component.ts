import { Component, ChangeDetectionStrategy, computed, HostListener, inject, OnInit, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { RouterLink } from '@angular/router';
import { AdminPgAuditLogService } from '../../../core/admin/admin-pgaudit-log.service';
import type { AuditPeriodPreset, PgAuditLogEntry, PgAuditLogStatus } from '../../../core/admin/admin.models';
import { AUDIT_PAGE_SIZE } from '../../../core/organization/organization.models';
import { ToastService } from '../../../core/toast/toast.service';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-pgaudit-logs-panel',
  standalone: true,
  imports: [LucideAngularModule, RouterLink, PaginationComponent],
  templateUrl: './pgaudit-logs-panel.component.html',
  styleUrl: './pgaudit-logs-panel.component.css',
})
export class PgauditLogsPanelComponent implements OnInit {
  private readonly pgAuditLogService = inject(AdminPgAuditLogService);
  private readonly toast = inject(ToastService);

  readonly initialLoading = signal(true);
  readonly tableLoading = signal(false);
  readonly refreshing = signal(false);
  readonly searchPending = signal(false);
  readonly filtersExpanded = signal(false);
  readonly detailOpen = signal(false);
  readonly selectedEntry = signal<PgAuditLogEntry | null>(null);
  readonly entries = signal<PgAuditLogEntry[]>([]);
  readonly available = signal(false);
  readonly viewerEnabled = signal(false);
  readonly statusReason = signal<PgAuditLogStatus['reason'] | null>(null);
  readonly availabilityMessage = signal<string | null>(null);
  readonly logDirectory = signal<string | null>(null);

  readonly search = signal('');
  readonly dbUser = signal('');
  readonly category = signal('');
  readonly period = signal<AuditPeriodPreset>('all');
  readonly from = signal('');
  readonly to = signal('');

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly pageSize = AUDIT_PAGE_SIZE;

  readonly categoryOptions: PgAuditLogEntry['category'][] = ['WRITE', 'DDL', 'ROLE', 'READ', 'MISC'];

  readonly periodPresets: { value: AuditPeriodPreset; label: string }[] = [
    { value: 'all', label: 'All' },
    { value: '24h', label: '24h' },
    { value: '7d', label: '7d' },
    { value: '30d', label: '30d' },
  ];

  readonly hasActiveFilters = computed(
    () =>
      !!this.search().trim() ||
      !!this.dbUser().trim() ||
      !!this.category() ||
      this.period() !== 'all' ||
      !!this.from().trim() ||
      !!this.to().trim(),
  );

  readonly resultsSummary = computed(() => {
    if (!this.available()) {
      return this.availabilityMessage() ?? 'Database audit logs are unavailable';
    }
    if (this.searchPending()) return 'Updating results…';
    const total = this.totalElements();
    if (total === 0) return 'No pgAudit entries found in scanned log files';
    const pages = this.totalPages();
    if (pages <= 1) return `${total} database audit entries · click a row for details`;
    return `${total} entries · page ${this.page() + 1}/${pages}`;
  });

  private filterTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    void this.load();
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (!this.detailOpen()) return;

    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeDetail();
    }
  }

  toggleFilters(): void {
    this.filtersExpanded.update((open) => !open);
  }

  openDetail(entry: PgAuditLogEntry): void {
    this.selectedEntry.set(entry);
    this.detailOpen.set(true);
  }

  closeDetail(): void {
    this.detailOpen.set(false);
    this.selectedEntry.set(null);
  }

  async refresh(): Promise<void> {
    this.refreshing.set(true);
    try {
      await this.load();
      this.toast.success('Database audit logs updated');
    } finally {
      this.refreshing.set(false);
    }
  }

  onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
    this.scheduleReload(300);
  }

  onDbUserInput(event: Event): void {
    this.dbUser.set((event.target as HTMLInputElement).value);
    this.scheduleReload(300);
  }

  onCategoryChange(event: Event): void {
    this.category.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
    void this.load();
  }

  onPeriodChange(preset: AuditPeriodPreset): void {
    this.period.set(preset);
    if (preset !== 'all') {
      this.from.set('');
      this.to.set('');
    }
    this.page.set(0);
    void this.load();
  }

  onFromChange(event: Event): void {
    this.from.set((event.target as HTMLInputElement).value);
    this.period.set('all');
    this.scheduleReload(400);
  }

  onToChange(event: Event): void {
    this.to.set((event.target as HTMLInputElement).value);
    this.period.set('all');
    this.scheduleReload(400);
  }

  async onPageChange(nextPage: number): Promise<void> {
    this.page.set(nextPage);
    this.closeDetail();
    await this.load();
  }

  clearFilters(): void {
    this.search.set('');
    this.dbUser.set('');
    this.category.set('');
    this.period.set('all');
    this.from.set('');
    this.to.set('');
    this.page.set(0);
    void this.load();
  }

  categoryBadgeClass(category: string | null): string {
    switch (category?.toUpperCase()) {
      case 'DDL':
      case 'ROLE':
        return 'badge-pill badge-pill--warning audit-action';
      case 'WRITE':
        return 'badge-pill badge-pill--error audit-action';
      case 'READ':
        return 'badge-pill badge-pill--success audit-action';
      default:
        return 'badge-pill badge-pill--neutral audit-action';
    }
  }

  summaryLine(entry: PgAuditLogEntry): string {
    if (entry.statement) return entry.statement;
    if (entry.objectType && entry.objectName) return `${entry.objectType} · ${entry.objectName}`;
    return entry.command ?? entry.rawLine;
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
      second: '2-digit',
    });
  }

  async copyText(label: string, value: string | null): Promise<void> {
    if (!value) return;
    try {
      await navigator.clipboard.writeText(value);
      this.toast.success(`${label} copied`);
    } catch {
      this.toast.error('Could not copy to clipboard');
    }
  }

  private scheduleReload(delayMs: number): void {
    this.page.set(0);
    this.searchPending.set(true);
    if (this.filterTimer) clearTimeout(this.filterTimer);
    this.filterTimer = setTimeout(() => void this.load(), delayMs);
  }

  private async load(): Promise<void> {
    const isFirstLoad = this.initialLoading();
    if (!isFirstLoad) this.tableLoading.set(true);

    try {
      const status = await this.pgAuditLogService.status();
      this.available.set(status.available);
      this.viewerEnabled.set(status.viewerEnabled);
      this.statusReason.set(status.reason);
      this.availabilityMessage.set(status.message);
      this.logDirectory.set(status.logDirectory);

      if (!status.available) {
        this.entries.set([]);
        this.totalPages.set(0);
        this.totalElements.set(0);
        return;
      }

      const res = await this.pgAuditLogService.list({
        page: this.page(),
        size: this.pageSize,
        q: this.search().trim() || undefined,
        user: this.dbUser().trim() || undefined,
        category: this.category() || undefined,
        from: this.resolveFrom(),
        to: this.resolveTo(),
      });

      this.entries.set(res.content);
      this.totalPages.set(res.totalPages);
      this.totalElements.set(res.totalElements);
      this.available.set(res.available);
      this.availabilityMessage.set(res.availabilityMessage);
    } catch {
      this.toast.error('Failed to load database audit logs');
    } finally {
      this.initialLoading.set(false);
      this.tableLoading.set(false);
      this.searchPending.set(false);
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
