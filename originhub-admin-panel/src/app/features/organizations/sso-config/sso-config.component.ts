import { Component, ChangeDetectionStrategy, inject, input, output, signal, effect } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule } from 'lucide-angular';
import type { OrganizationDetail, SsoConfigRequest } from '../../../core/organization/organization.models';
import { OrganizationService } from '../../../core/organization/organization.service';
import { ToastService } from '../../../core/toast/toast.service';
import { SsoSwitchComponent } from '../../../shared/components/sso-switch/sso-switch.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-sso-config',
  standalone: true,
  imports: [ReactiveFormsModule, LucideAngularModule, SsoSwitchComponent],
  templateUrl: './sso-config.component.html',
})
export class SsoConfigComponent {
  private readonly fb = inject(FormBuilder);
  private readonly organizationService = inject(OrganizationService);
  private readonly toast = inject(ToastService);

  readonly organization = input.required<OrganizationDetail>();
  readonly updated = output<OrganizationDetail>();

  readonly saving = signal(false);
  readonly toggling = signal(false);
  readonly testing = signal(false);
  readonly testResult = signal<{ success: boolean; message: string; details?: string | null } | null>(null);
  readonly expanded = signal(false);
  private lastExpandedOrgSlug: string | null = null;

  readonly form = this.fb.nonNullable.group({
    ssoEnabled: [false],
    idpMetadataUri: [''],
    emailAttribute: ['email', [Validators.required]],
    usernameAttribute: [''],
    spEntityId: [''],
  });

  constructor() {
    effect(() => {
      const org = this.organization();
      if (this.lastExpandedOrgSlug !== org.slug) {
        this.lastExpandedOrgSlug = org.slug;
        this.expanded.set(false);
      }
      this.form.reset({
        ssoEnabled: org.ssoEnabled ?? false,
        idpMetadataUri: org.idpMetadataUri ?? '',
        emailAttribute: org.emailAttribute ?? 'email',
        usernameAttribute: org.usernameAttribute ?? '',
        spEntityId: org.spEntityId ?? '',
      });
      this.testResult.set(null);
    });
  }

  toggleExpanded(): void {
    this.expanded.update((open) => !open);
  }

  async onQuickToggle(enabled: boolean): Promise<void> {
    const org = this.organization();
    this.toggling.set(true);

    try {
      const updated = await this.organizationService.setSsoEnabled(org.slug, enabled);
      this.form.patchValue({ ssoEnabled: updated.ssoEnabled });
      this.toast.success(updated.ssoEnabled ? 'SSO enabled' : 'SSO disabled');
      this.updated.emit(updated);
    } catch (e) {
      this.toast.error(this.extractErrorMessage(e, 'Failed to update SSO status'));
    } finally {
      this.toggling.set(false);
    }
  }

  async onSave(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();
    const body: SsoConfigRequest = {
      ssoEnabled: raw.ssoEnabled,
      idpMetadataUri: raw.idpMetadataUri.trim(),
      emailAttribute: raw.emailAttribute.trim() || 'email',
      usernameAttribute: raw.usernameAttribute.trim() || null,
      spEntityId: raw.spEntityId.trim() || null,
    };

    try {
      const updated = await this.organizationService.updateSso(this.organization().slug, body);
      this.toast.success('SSO configuration saved');
      this.updated.emit(updated);
    } catch (e) {
      this.toast.error(this.extractErrorMessage(e, 'Failed to save SSO configuration'));
    } finally {
      this.saving.set(false);
    }
  }

  async onTest(): Promise<void> {
    this.testing.set(true);
    this.testResult.set(null);
    try {
      const result = await this.organizationService.testSso(this.organization().slug);
      this.testResult.set({
        success: result.valid,
        message: result.message,
        details: result.cached ? 'Metadata cached in database' : null,
      });
      if (result.valid) {
        this.toast.success(result.message || 'SSO connection test passed');
        if (!this.organization().ssoEnabled) {
          this.form.patchValue({ ssoEnabled: true });
          this.testResult.set({
            success: true,
            message: 'Metadata valid — toggle SSO on above or save to enable login.',
            details: 'Test connection does not enable SSO by itself.',
          });
        }
      } else {
        this.toast.error(result.message || 'SSO connection test failed');
      }
    } catch (e) {
      const msg = this.extractErrorMessage(e, 'SSO connection test failed');
      this.testResult.set({ success: false, message: msg });
      this.toast.error(msg);
    } finally {
      this.testing.set(false);
    }
  }

  private extractErrorMessage(e: unknown, fallback: string): string {
    if (e instanceof HttpErrorResponse) {
      const code = typeof e.error === 'object' && e.error?.message ? String(e.error.message) : '';
      if (code === 'idpMetadataUriRequired') {
        return 'IdP metadata URI is required before enabling SAML.';
      }
      if (code === 'ssoProtocolConflict') {
        return 'Disable the other SSO method first (SAML or LDAP).';
      }
      return e.error?.message ?? e.statusText ?? fallback;
    }
    return (e as Error).message ?? fallback;
  }
}
