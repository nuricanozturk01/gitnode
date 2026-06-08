import { Component, ChangeDetectionStrategy, inject, input, output, signal, effect } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule } from 'lucide-angular';
import type { LdapConfigRequest, OrganizationDetail } from '../../../core/organization/organization.models';
import { OrganizationService } from '../../../core/organization/organization.service';
import { ToastService } from '../../../core/toast/toast.service';
import { SsoSwitchComponent } from '../../../shared/components/sso-switch/sso-switch.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-ldap-config',
  standalone: true,
  imports: [ReactiveFormsModule, LucideAngularModule, SsoSwitchComponent],
  templateUrl: './ldap-config.component.html',
})
export class LdapConfigComponent {
  private readonly fb = inject(FormBuilder);
  private readonly organizationService = inject(OrganizationService);
  private readonly toast = inject(ToastService);

  readonly organization = input.required<OrganizationDetail>();
  readonly updated = output<OrganizationDetail>();

  readonly saving = signal(false);
  readonly toggling = signal(false);
  readonly testing = signal(false);
  readonly testResult = signal<{ success: boolean; message: string } | null>(null);
  readonly expanded = signal(false);
  private lastExpandedOrgSlug: string | null = null;

  readonly form = this.fb.nonNullable.group({
    ldapEnabled: [false],
    url: ['', [Validators.required]],
    baseDn: ['', [Validators.required]],
    managerDn: [''],
    managerPassword: [''],
    userSearchBase: ['ou=people', [Validators.required]],
    userSearchFilter: ['(uid={0})', [Validators.required]],
    emailAttribute: ['mail', [Validators.required]],
    displayNameAttribute: ['cn', [Validators.required]],
    useStartTls: [false],
    groupSearchBase: ['ou=groups'],
    groupSearchFilter: ['(memberUid={0})'],
    groupRoleAttribute: ['cn'],
    adminGroupDns: [''],
  });

  constructor() {
    effect(() => {
      const org = this.organization();
      if (this.lastExpandedOrgSlug !== org.slug) {
        this.lastExpandedOrgSlug = org.slug;
        this.expanded.set(false);
      }
      this.form.reset({
        ldapEnabled: org.ldapEnabled ?? false,
        url: org.ldapUrl ?? '',
        baseDn: org.ldapBaseDn ?? '',
        managerDn: org.ldapManagerDn ?? '',
        managerPassword: '',
        userSearchBase: org.ldapUserSearchBase ?? 'ou=people',
        userSearchFilter: org.ldapUserSearchFilter ?? '(uid={0})',
        emailAttribute: org.ldapEmailAttribute ?? 'mail',
        displayNameAttribute: org.ldapDisplayNameAttribute ?? 'cn',
        useStartTls: org.ldapUseStartTls ?? false,
        groupSearchBase: org.ldapGroupSearchBase ?? 'ou=groups',
        groupSearchFilter: org.ldapGroupSearchFilter ?? '(memberUid={0})',
        groupRoleAttribute: org.ldapGroupRoleAttribute ?? 'cn',
        adminGroupDns: org.ldapAdminGroupDns ?? '',
      });
      this.testResult.set(null);
    });
  }

  toggleExpanded(): void {
    this.expanded.update((open) => !open);
  }

  async onQuickToggle(enabled: boolean): Promise<void> {
    if (enabled) {
      if (this.form.invalid) {
        this.form.markAllAsTouched();
        this.toast.error('Enter LDAP URL and Base DN, then enable again.');
        return;
      }
      await this.persistLdap(true, 'LDAP enabled', true);
      return;
    }

    const org = this.organization();
    this.toggling.set(true);
    try {
      const updated = await this.organizationService.setLdapEnabled(org.slug, false);
      this.form.patchValue({ ldapEnabled: false });
      this.toast.success('LDAP disabled');
      this.updated.emit(updated);
    } catch (e) {
      this.toast.error(this.extractErrorMessage(e, 'Failed to disable LDAP'));
    } finally {
      this.toggling.set(false);
    }
  }

  async onSave(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    await this.persistLdap(this.form.getRawValue().ldapEnabled, 'LDAP configuration saved');
  }

  async onTest(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.error('Enter LDAP URL and Base DN before testing.');
      return;
    }

    this.testing.set(true);
    this.testResult.set(null);
    try {
      const updated = await this.organizationService.updateLdap(
        this.organization().slug,
        this.buildRequest(this.form.getRawValue().ldapEnabled),
      );
      this.form.patchValue({ ldapEnabled: updated.ldapEnabled, managerPassword: '' });
      this.updated.emit(updated);

      const result = await this.organizationService.testLdap(this.organization().slug);
      this.testResult.set({ success: result.valid, message: result.message });
      if (result.valid) {
        this.toast.success(result.message || 'LDAP connection test passed');
      } else {
        this.toast.error(result.message || 'LDAP connection test failed');
      }
    } catch (e) {
      const msg = this.extractErrorMessage(e, 'LDAP connection test failed');
      this.testResult.set({ success: false, message: msg });
      this.toast.error(msg);
    } finally {
      this.testing.set(false);
    }
  }

  private async persistLdap(
    ldapEnabled: boolean,
    successMessage: string | null,
    useToggleSpinner = false,
  ): Promise<void> {
    if (useToggleSpinner) {
      this.toggling.set(true);
    } else {
      this.saving.set(true);
    }

    try {
      const updated = await this.organizationService.updateLdap(
        this.organization().slug,
        this.buildRequest(ldapEnabled),
      );
      this.form.patchValue({ ldapEnabled: updated.ldapEnabled, managerPassword: '' });
      if (successMessage) {
        this.toast.success(successMessage);
      }
      this.updated.emit(updated);
    } catch (e) {
      this.toast.error(this.extractErrorMessage(e, 'Failed to save LDAP configuration'));
      throw e;
    } finally {
      if (useToggleSpinner) {
        this.toggling.set(false);
      } else {
        this.saving.set(false);
      }
    }
  }

  private buildRequest(ldapEnabled: boolean): LdapConfigRequest {
    const raw = this.form.getRawValue();
    return {
      ldapEnabled,
      url: raw.url.trim(),
      baseDn: raw.baseDn.trim(),
      managerDn: raw.managerDn.trim() || null,
      managerPassword: raw.managerPassword.trim() || null,
      userSearchBase: raw.userSearchBase.trim() || 'ou=people',
      userSearchFilter: raw.userSearchFilter.trim() || '(uid={0})',
      emailAttribute: raw.emailAttribute.trim() || 'mail',
      displayNameAttribute: raw.displayNameAttribute.trim() || 'cn',
      useStartTls: raw.useStartTls,
      groupSearchBase: raw.groupSearchBase.trim() || null,
      groupSearchFilter: raw.groupSearchFilter.trim() || null,
      groupRoleAttribute: raw.groupRoleAttribute.trim() || null,
      adminGroupDns: raw.adminGroupDns.trim() || null,
    };
  }

  private extractErrorMessage(e: unknown, fallback: string): string {
    if (e instanceof HttpErrorResponse) {
      const code = typeof e.error === 'object' && e.error?.message ? String(e.error.message) : '';
      if (code === 'ldapConfigIncomplete') {
        return 'LDAP URL and Base DN are required before enabling.';
      }
      if (code === 'ssoProtocolConflict') {
        return 'Disable the other SSO method first (SAML or LDAP).';
      }
      if (code === 'ldapConnectionFailed') {
        return 'Could not connect to the LDAP server. Check URL, manager credentials, and TLS settings.';
      }
      return e.error?.message ?? e.statusText ?? fallback;
    }
    return (e as Error).message ?? fallback;
  }
}
