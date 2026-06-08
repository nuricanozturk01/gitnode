import { Component, ChangeDetectionStrategy, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AdminPgAuditLogService } from '../../core/admin/admin-pgaudit-log.service';
import { AdminSettingsService } from '../../core/admin/admin-settings.service';
import type { PgAuditLogStatus, PlatformAdminsResponse } from '../../core/admin/admin.models';
import { ToastService } from '../../core/toast/toast.service';
import { SsoSwitchComponent } from '../../shared/components/sso-switch/sso-switch.component';
import { apiErrorMessage } from '../../shared/utils/api-error';
import { formatCacheTtl, minutesToSeconds, secondsToMinutes } from '../../shared/utils/cache-ttl';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-settings',
  standalone: true,
  imports: [ReactiveFormsModule, LucideAngularModule, RouterLink, SsoSwitchComponent],
  templateUrl: './settings.page.html',
  styleUrl: './settings.page.css',
})
export class SettingsPage implements OnInit {
  private readonly adminSettingsService = inject(AdminSettingsService);
  private readonly pgAuditLogService = inject(AdminPgAuditLogService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly formatCacheTtl = formatCacheTtl;
  readonly minutesToSeconds = minutesToSeconds;

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly savingToggles = signal(false);
  readonly error = signal<string | null>(null);
  readonly platformAdmins = signal<PlatformAdminsResponse | null>(null);
  readonly pgAuditStatus = signal<PgAuditLogStatus | null>(null);

  readonly form = this.fb.nonNullable.group({
    statsCacheMinutes: [5, [Validators.required, Validators.min(1), Validators.max(1440)]],
  });

  readonly toggleForm = this.fb.nonNullable.group({
    pgAuditViewerEnabled: [false],
    modulithEventsViewerEnabled: [false],
  });

  readonly pgAuditReady = computed(() => this.pgAuditStatus()?.available ?? false);

  readonly pgAuditStatusLabel = computed(() => {
    const status = this.pgAuditStatus();
    if (!status) return 'Checking…';
    if (status.available) return 'Ready';
    if (!status.viewerEnabled || status.reason === 'VIEWER_DISABLED') return 'Disabled';
    if (status.reason === 'LOG_DIRECTORY_NOT_CONFIGURED') return 'Needs log path';
    if (status.reason === 'LOG_DIRECTORY_NOT_FOUND') return 'Path missing';
    if (status.reason === 'LOG_DIRECTORY_NOT_READABLE') return 'Not readable';
    return 'Unavailable';
  });

  ngOnInit(): void {
    void this.load();
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set(null);

    try {
      const statsCacheTtlSeconds = minutesToSeconds(this.form.controls.statsCacheMinutes.value);
      await this.adminSettingsService.updateStatsCacheTtl(statsCacheTtlSeconds);
      this.toast.success('Statistics settings saved');
    } catch (e) {
      const msg = apiErrorMessage(e, 'Failed to save settings');
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.saving.set(false);
    }
  }

  async onSaveToggles(): Promise<void> {
    this.savingToggles.set(true);
    this.error.set(null);

    try {
      await this.adminSettingsService.updateFeatureToggles({
        pgAuditViewerEnabled: this.toggleForm.controls.pgAuditViewerEnabled.value,
        modulithEventsViewerEnabled: this.toggleForm.controls.modulithEventsViewerEnabled.value,
      });
      await this.refreshPgAuditStatus();
      this.toast.success('Diagnostic viewer settings saved');
    } catch (e) {
      const msg = apiErrorMessage(e, 'Failed to save diagnostic viewer settings');
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingToggles.set(false);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);

    try {
      const [settings, admins] = await Promise.all([
        this.adminSettingsService.getSettings(),
        this.adminSettingsService.getPlatformAdmins(),
      ]);
      await this.refreshPgAuditStatus();
      this.platformAdmins.set(admins);
      this.form.patchValue({
        statsCacheMinutes: secondsToMinutes(settings.statsCacheTtlSeconds),
      });
      this.toggleForm.patchValue({
        pgAuditViewerEnabled: settings.pgAuditViewerEnabled,
        modulithEventsViewerEnabled: settings.modulithEventsViewerEnabled,
      });
    } catch (e) {
      const msg = apiErrorMessage(e, 'Failed to load settings');
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.loading.set(false);
    }
  }

  private async refreshPgAuditStatus(): Promise<void> {
    try {
      this.pgAuditStatus.set(await this.pgAuditLogService.status());
    } catch {
      this.pgAuditStatus.set(null);
    }
  }
}
