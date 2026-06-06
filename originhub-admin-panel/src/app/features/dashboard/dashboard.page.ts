import { Component, ChangeDetectionStrategy, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AdminStatsService } from '../../core/admin/admin-stats.service';
import { AdminAuditLogService } from '../../core/admin/admin-audit-log.service';
import { AdminWebhookDlqService } from '../../core/admin/admin-webhook-dlq.service';
import type {
  AuditLogEntry,
  StatsActivityPoint,
  StatsContributor,
  StatsOverview,
  StatsPeriod,
} from '../../core/admin/admin.models';
import { OrganizationService } from '../../core/organization/organization.service';
import type { OrganizationSummary } from '../../core/organization/organization.models';
import { ToastService } from '../../core/toast/toast.service';
import { formatBytesAsGb } from '../../shared/utils/format-bytes';
import { formatCacheTtl } from '../../shared/utils/cache-ttl';
import {
  ActivityChartComponent,
  type ChartSeries,
} from '../../shared/components/activity-chart/activity-chart.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, ActivityChartComponent, PaginationComponent],
  templateUrl: './dashboard.page.html',
})
export class DashboardPage implements OnInit {
  private readonly adminStatsService = inject(AdminStatsService);
  private readonly adminAuditLogService = inject(AdminAuditLogService);
  private readonly adminWebhookDlqService = inject(AdminWebhookDlqService);
  private readonly organizationService = inject(OrganizationService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly period = signal<StatsPeriod>('week');

  readonly overview = signal<StatsOverview | null>(null);
  readonly cachedAt = signal<string | null>(null);
  readonly cacheTtlSeconds = signal(300);
  readonly fromCache = signal(false);
  readonly contributors = signal<StatsContributor[]>([]);
  readonly activity = signal<StatsActivityPoint[]>([]);
  readonly recentAudit = signal<AuditLogEntry[]>([]);
  readonly dlqPending = signal(0);

  readonly organizations = signal<OrganizationSummary[]>([]);
  readonly orgPage = signal(0);
  readonly orgTotalPages = signal(0);
  readonly orgTotalElements = signal(0);

  readonly cacheTtlLabel = computed(() => formatCacheTtl(this.cacheTtlSeconds()));

  readonly cachedAtLabel = computed(() => {
    const value = this.cachedAt();
    if (!value) return null;

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;

    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  });

  readonly chartLabels = computed(() =>
    this.activity().map((point) => {
      const date = new Date(point.date);
      return Number.isNaN(date.getTime())
        ? point.date
        : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    }),
  );

  readonly chartSeries = computed((): ChartSeries[] => [
    {
      key: 'repos',
      label: 'New repos',
      color: 'oklch(74% 0.16 166)',
      values: this.activity().map((p) => p.repos),
    },
    {
      key: 'uploads',
      label: 'Upload proxy',
      color: 'oklch(70% 0.14 230)',
      values: this.activity().map((p) => p.uploads),
    },
  ]);

  readonly contributorChartSeries = computed((): ChartSeries[] => {
    const top = this.contributors().slice(0, 8);
    return [
      {
        key: 'repos',
        label: 'Repos created',
        color: 'oklch(74% 0.16 166)',
        values: top.map((c) => c.count),
      },
    ];
  });

  readonly contributorChartLabels = computed(() =>
    this.contributors()
      .slice(0, 8)
      .map((c) => c.username),
  );

  readonly disabledUsers = computed(() => {
    const overview = this.overview();
    if (!overview?.totalUsers || overview.enabledUsers == null) return 0;
    return Math.max(0, overview.totalUsers - overview.enabledUsers);
  });

  formatAuditTime(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  ngOnInit(): void {
    void this.load(false);
  }

  async refreshFromDatabase(): Promise<void> {
    if (this.refreshing()) return;

    this.refreshing.set(true);
    try {
      await Promise.all([this.loadOverview(true), this.loadPeriodStats(true)]);
      this.toast.success('Statistics refreshed from database');
    } catch {
      this.toast.error('Failed to refresh statistics');
    } finally {
      this.refreshing.set(false);
    }
  }

  async setPeriod(next: StatsPeriod): Promise<void> {
    if (this.period() === next) return;
    this.period.set(next);
    await this.loadPeriodStats(false);
  }

  async onOrgPageChange(nextPage: number): Promise<void> {
    this.orgPage.set(nextPage);
    await this.loadOrganizations();
  }

  formatStorage(bytes: number | undefined): string {
    return formatBytesAsGb(bytes ?? 0);
  }

  formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
    });
  }

  private async load(refresh: boolean): Promise<void> {
    this.loading.set(true);
    try {
      await Promise.all([
        this.loadOverview(refresh),
        this.loadPeriodStats(refresh),
        this.loadOrganizations(),
        this.loadRecentAudit(),
        this.loadDlqSummary(),
      ]);
    } catch {
      this.toast.error('Failed to load dashboard data');
    } finally {
      this.loading.set(false);
    }
  }

  private async loadRecentAudit(): Promise<void> {
    try {
      const res = await this.adminAuditLogService.list({ page: 0, size: 8 });
      this.recentAudit.set(res.content);
    } catch {
      this.recentAudit.set([]);
    }
  }

  private async loadDlqSummary(): Promise<void> {
    try {
      const summary = await this.adminWebhookDlqService.summary();
      this.dlqPending.set(summary.pending);
    } catch {
      this.dlqPending.set(0);
    }
  }

  private async loadOverview(refresh: boolean): Promise<void> {
    try {
      const response = await this.adminStatsService.getOverview(refresh);
      this.overview.set(response.overview);
      this.cachedAt.set(response.cachedAt);
      this.cacheTtlSeconds.set(response.cacheTtlSeconds);
      this.fromCache.set(response.fromCache);
    } catch {
      this.overview.set(null);
      this.cachedAt.set(null);
    }
  }

  private async loadPeriodStats(refresh: boolean): Promise<void> {
    const period = this.period();
    try {
      const [repos, uploads] = await Promise.all([
        this.adminStatsService.getRepos(period, refresh),
        this.adminStatsService.getUploads(period, refresh),
      ]);
      this.contributors.set(repos.contributors ?? []);
      this.activity.set(this.mergeActivity(repos.activity ?? [], uploads.activity ?? []));
    } catch {
      this.contributors.set([]);
      this.activity.set([]);
    }
  }

  private async loadOrganizations(): Promise<void> {
    try {
      const res = await this.organizationService.list({ page: this.orgPage(), size: 10 });
      this.organizations.set(res.content);
      this.orgTotalPages.set(res.totalPages);
      this.orgTotalElements.set(res.totalElements);
    } catch {
      this.organizations.set([]);
      this.orgTotalPages.set(0);
      this.orgTotalElements.set(0);
    }
  }

  private mergeActivity(repos: StatsActivityPoint[], uploads: StatsActivityPoint[]): StatsActivityPoint[] {
    const byDate = new Map<string, StatsActivityPoint>();

    for (const point of repos) {
      byDate.set(point.date, {
        date: point.date,
        repos: point.repos,
        uploads: 0,
      });
    }

    for (const point of uploads) {
      const existing = byDate.get(point.date);
      if (existing) {
        existing.uploads = point.uploads;
      } else {
        byDate.set(point.date, {
          date: point.date,
          repos: 0,
          uploads: point.uploads,
        });
      }
    }

    return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date));
  }
}
