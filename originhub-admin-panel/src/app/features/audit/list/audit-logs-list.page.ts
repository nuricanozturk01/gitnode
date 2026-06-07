import { Component, ChangeDetectionStrategy, computed, HostListener, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AdminAuditLogService } from '../../../core/admin/admin-audit-log.service';
import type { AuditLogEntry, AuditPeriodPreset } from '../../../core/admin/admin.models';
import { AUDIT_PAGE_SIZE } from '../../../core/organization/organization.models';
import { ToastService } from '../../../core/toast/toast.service';
import { copyTextToClipboard } from '../../../shared/utils/clipboard.util';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-audit-logs-list',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, PaginationComponent],
  templateUrl: './audit-logs-list.page.html',
  styleUrl: './audit-logs-list.page.css',
})
export class AuditLogsListPage implements OnInit {
  private readonly auditLogService = inject(AdminAuditLogService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);

  readonly initialLoading = signal(true);
  readonly tableLoading = signal(false);
  readonly refreshing = signal(false);
  readonly searchPending = signal(false);
  readonly filtersLoading = signal(true);
  readonly filtersExpanded = signal(false);
  readonly detailOpen = signal(false);
  readonly selectedEntry = signal<AuditLogEntry | null>(null);
  readonly entries = signal<AuditLogEntry[]>([]);
  readonly actionOptions = signal<string[]>([]);
  readonly entityTypeOptions = signal<string[]>([]);

  readonly search = signal('');
  readonly actor = signal('');
  readonly action = signal('');
  readonly entityType = signal('');
  readonly entityId = signal('');
  readonly ipAddress = signal('');
  readonly period = signal<AuditPeriodPreset>('all');
  readonly from = signal('');
  readonly to = signal('');

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  readonly pageSize = AUDIT_PAGE_SIZE;

  readonly periodPresets: { value: AuditPeriodPreset; label: string }[] = [
    { value: 'all', label: 'All' },
    { value: '24h', label: '24h' },
    { value: '7d', label: '7d' },
    { value: '30d', label: '30d' },
  ];

  readonly activeFilterChips = computed(() => {
    const chips: string[] = [];

    if (this.search().trim()) chips.push('search');
    if (this.actor().trim()) chips.push('actor');
    if (this.action()) chips.push('action');
    if (this.entityType()) chips.push('entityType');
    if (this.entityId().trim()) chips.push('entityId');
    if (this.ipAddress().trim()) chips.push('ipAddress');
    if (this.period() !== 'all') chips.push('period');
    if (this.from().trim()) chips.push('from');
    if (this.to().trim()) chips.push('to');

    return chips;
  });

  readonly hasActiveFilters = computed(() => this.activeFilterChips().length > 0);
  readonly activeFilterCount = computed(() => this.activeFilterChips().length);

  readonly selectedIndex = computed(() => {
    const entry = this.selectedEntry();
    if (!entry) return -1;
    return this.entries().findIndex((item) => item.id === entry.id);
  });

  readonly canShowPreviousDetail = computed(() => this.selectedIndex() > 0);
  readonly canShowNextDetail = computed(() => {
    const index = this.selectedIndex();
    return index >= 0 && index < this.entries().length - 1;
  });

  readonly resultsSummary = computed(() => {
    if (this.searchPending()) return 'Updating results…';

    const total = this.totalElements();
    if (total === 0) return 'No entries found';
    const pages = this.totalPages();
    if (pages <= 1) return `${total} entries · click a row for details`;
    return `${total} entries · page ${this.page() + 1}/${pages}`;
  });

  private filterTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    const actor = this.route.snapshot.queryParamMap.get('actor')?.trim();
    if (actor) {
      this.actor.set(actor);
      this.filtersExpanded.set(true);
    }

    void this.loadFilters();
    void this.load();
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (!this.detailOpen()) return;

    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeDetail();
      return;
    }

    if (event.key === 'ArrowLeft' && this.canShowPreviousDetail()) {
      event.preventDefault();
      this.showPreviousDetail();
      return;
    }

    if (event.key === 'ArrowRight' && this.canShowNextDetail()) {
      event.preventDefault();
      this.showNextDetail();
    }
  }

  toggleFilters(): void {
    this.filtersExpanded.update((open) => !open);
  }

  openDetail(entry: AuditLogEntry): void {
    this.selectedEntry.set(entry);
    this.detailOpen.set(true);
  }

  closeDetail(): void {
    this.detailOpen.set(false);
    this.selectedEntry.set(null);
  }

  showPreviousDetail(): void {
    const index = this.selectedIndex();
    if (index <= 0) return;
    this.selectedEntry.set(this.entries()[index - 1] ?? null);
  }

  showNextDetail(): void {
    const index = this.selectedIndex();
    const next = this.entries()[index + 1];
    if (!next) return;
    this.selectedEntry.set(next);
  }

  onRowActivate(event: Event, entry: AuditLogEntry): void {
    event.preventDefault();
    this.openDetail(entry);
  }

  rowAriaLabel(entry: AuditLogEntry): string {
    return `View audit event ${entry.action} by ${entry.actorUsername ?? 'system'} at ${this.formatDateTime(entry.occurredAt)}`;
  }

  async refresh(): Promise<void> {
    this.refreshing.set(true);
    try {
      await this.load();
      this.toast.success('Audit logs updated');
    } finally {
      this.refreshing.set(false);
    }
  }

  clearSearch(): void {
    this.search.set('');
    this.page.set(0);
    void this.load();
  }

  onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
    this.scheduleReload(300);
  }

  onActorInput(event: Event): void {
    this.actor.set((event.target as HTMLInputElement).value);
    this.scheduleReload(300);
  }

  onEntityIdInput(event: Event): void {
    this.entityId.set((event.target as HTMLInputElement).value);
    this.scheduleReload(300);
  }

  onIpAddressInput(event: Event): void {
    this.ipAddress.set((event.target as HTMLInputElement).value);
    this.scheduleReload(300);
  }

  onActionChange(event: Event): void {
    this.action.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
    void this.load();
  }

  onEntityTypeChange(event: Event): void {
    this.entityType.set((event.target as HTMLSelectElement).value);
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
    this.actor.set('');
    this.action.set('');
    this.entityType.set('');
    this.entityId.set('');
    this.ipAddress.set('');
    this.period.set('all');
    this.from.set('');
    this.to.set('');
    this.page.set(0);
    this.filtersExpanded.set(false);
    void this.load();
  }

  actionBadgeClass(action: string): string {
    const normalized = action.toUpperCase();

    if (
      normalized.includes('DELETE') ||
      normalized.includes('REMOVED') ||
      normalized.includes('FAILED') ||
      (normalized.includes('DISABLED') && !normalized.includes('ENABLED'))
    ) {
      return 'badge-pill badge-pill--error audit-action';
    }

    if (
      normalized.includes('CREATE') ||
      normalized.includes('CREATED') ||
      normalized.includes('SUCCESS') ||
      normalized.includes('ENABLED') ||
      normalized.includes('INVITED')
    ) {
      return 'badge-pill badge-pill--success audit-action';
    }

    if (
      normalized.includes('UPDATE') ||
      normalized.includes('CHANGED') ||
      normalized.includes('RENAMED') ||
      normalized.includes('MIGRAT') ||
      normalized.includes('TEST')
    ) {
      return 'badge-pill badge-pill--warning audit-action';
    }

    return 'badge-pill badge-pill--neutral audit-action';
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

  formatCompactTime(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';

    const now = new Date();
    const sameDay =
      date.getFullYear() === now.getFullYear() &&
      date.getMonth() === now.getMonth() &&
      date.getDate() === now.getDate();

    if (sameDay) {
      return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    }

    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  formatRelativeTime(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';

    const diffSec = Math.round((Date.now() - date.getTime()) / 1000);
    if (diffSec < 45) return 'Just now';
    if (diffSec < 90) return '1 minute ago';

    const diffMin = Math.round(diffSec / 60);
    if (diffMin < 60) return `${diffMin} minutes ago`;

    const diffHours = Math.round(diffMin / 60);
    if (diffHours < 24) return `${diffHours} hours ago`;

    const diffDays = Math.round(diffHours / 24);
    if (diffDays < 7) return `${diffDays} days ago`;

    return this.formatDateTime(value);
  }

  contextLine(entry: AuditLogEntry): string {
    if (entry.details) return entry.details;
    if (entry.entityType && entry.entityId) return `${entry.entityType} · ${entry.entityId}`;
    return entry.entityType ?? entry.entityId ?? 'No additional context';
  }

  contextTitle(entry: AuditLogEntry): string {
    return this.contextLine(entry);
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

  async copyEntryJson(entry: AuditLogEntry): Promise<void> {
    try {
      await copyTextToClipboard(JSON.stringify(entry, null, 2));
      this.toast.success('Event copied as JSON');
    } catch {
      this.toast.error('Could not copy to clipboard');
    }
  }

  private scheduleReload(delayMs: number): void {
    this.page.set(0);
    this.searchPending.set(true);
    if (this.filterTimer) {
      clearTimeout(this.filterTimer);
    }
    this.filterTimer = setTimeout(() => {
      void this.load();
    }, delayMs);
  }

  private async loadFilters(): Promise<void> {
    this.filtersLoading.set(true);
    try {
      const res = await this.auditLogService.filters();
      this.actionOptions.set(res.actions);
      this.entityTypeOptions.set(res.entityTypes);
    } catch {
      this.toast.error('Failed to load audit filter options');
    } finally {
      this.filtersLoading.set(false);
    }
  }

  private async load(): Promise<void> {
    const isFirstLoad = this.initialLoading();
    if (!isFirstLoad) {
      this.tableLoading.set(true);
    }

    try {
      const res = await this.auditLogService.list({
        page: this.page(),
        size: this.pageSize,
        q: this.search().trim() || undefined,
        actor: this.actor().trim() || undefined,
        action: this.action() || undefined,
        entityType: this.entityType() || undefined,
        entityId: this.entityId().trim() || undefined,
        ipAddress: this.ipAddress().trim() || undefined,
        from: this.resolveFrom(),
        to: this.resolveTo(),
      });
      this.entries.set(res.content);
      this.totalPages.set(res.totalPages);
      this.totalElements.set(res.totalElements);

      const selected = this.selectedEntry();
      if (selected) {
        const stillVisible = res.content.find((item) => item.id === selected.id);
        this.selectedEntry.set(stillVisible ?? null);
        if (!stillVisible) {
          this.detailOpen.set(false);
        }
      }
    } catch {
      this.toast.error('Failed to load audit logs');
    } finally {
      this.initialLoading.set(false);
      this.tableLoading.set(false);
      this.searchPending.set(false);
    }
  }

  private resolveFrom(): string | undefined {
    const customFrom = this.from().trim();
    if (customFrom) {
      return new Date(customFrom).toISOString();
    }

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
