import { DatePipe, JsonPipe } from '@angular/common';
import { Component, ChangeDetectionStrategy, computed, inject, OnInit, signal } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { AdminSystemService } from '../../core/system/admin-system.service';
import type { SystemHealthComponent, SystemHealthResponse } from '../../core/admin/admin.models';
import { ToastService } from '../../core/toast/toast.service';
import { apiErrorMessage } from '../../shared/utils/api-error';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-system-health',
  standalone: true,
  imports: [LucideAngularModule, DatePipe, JsonPipe],
  templateUrl: './system-health.page.html',
})
export class SystemHealthPage implements OnInit {
  private readonly systemService = inject(AdminSystemService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly health = signal<SystemHealthResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly checkedAt = signal<string | null>(null);
  readonly metrics = signal<{ name: string; value: number }[]>([]);

  private readonly trackedMetrics = [
    'webhook.delivery.success',
    'webhook.delivery.failure',
    'webhook.dlq.retry.success',
    'webhook.dlq.retry.exhausted',
    'jvm.memory.used',
    'process.uptime',
  ];

  readonly components = computed(() => {
    const items = this.health()?.components;
    if (!items) return [] as { name: string; component: SystemHealthComponent }[];
    return Object.entries(items)
      .map(([name, component]) => ({ name, component }))
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  ngOnInit(): void {
    void this.load();
  }

  statusBadgeClass(status: string): string {
    switch (status.toUpperCase()) {
      case 'UP':
        return 'badge-pill badge-pill--success';
      case 'DOWN':
      case 'OUT_OF_SERVICE':
        return 'badge-pill badge-pill--error';
      default:
        return 'badge-pill';
    }
  }

  async refresh(): Promise<void> {
    this.refreshing.set(true);
    await this.load(false);
    this.refreshing.set(false);
  }

  formatMetricValue(value: number): string {
    if (Number.isNaN(value)) return '—';
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
    if (value >= 1_000) return `${(value / 1_000).toFixed(1)}k`;
    return String(Math.round(value * 100) / 100);
  }

  private async load(showSpinner = true): Promise<void> {
    if (showSpinner) {
      this.loading.set(true);
    }
    this.error.set(null);

    try {
      const [health, metricResults] = await Promise.all([this.systemService.getHealth(), this.loadMetrics()]);
      this.health.set(health);
      this.metrics.set(metricResults);
      this.checkedAt.set(new Date().toISOString());
    } catch (e) {
      const msg = apiErrorMessage(e, 'Failed to load system health');
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadMetrics(): Promise<{ name: string; value: number }[]> {
    const results: { name: string; value: number }[] = [];

    for (const name of this.trackedMetrics) {
      try {
        const metric = await this.systemService.getMetric(name);
        const value =
          metric.measurements?.find((m) => m.statistic === 'COUNT' || m.statistic === 'TOTAL')?.value ??
          metric.measurements?.[0]?.value ??
          0;
        results.push({ name, value });
      } catch {
        results.push({ name, value: NaN });
      }
    }

    return results;
  }
}
