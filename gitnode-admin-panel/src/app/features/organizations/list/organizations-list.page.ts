import { Component, ChangeDetectionStrategy, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { OrganizationService } from '../../../core/organization/organization.service';
import { ADMIN_PAGE_SIZE } from '../../../core/organization/organization.models';
import type { OrganizationSummary } from '../../../core/organization/organization.models';
import { ToastService } from '../../../core/toast/toast.service';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';
import { SsoSwitchComponent } from '../../../shared/components/sso-switch/sso-switch.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-organizations-list',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, PaginationComponent, SsoSwitchComponent],
  templateUrl: './organizations-list.page.html',
})
export class OrganizationsListPage implements OnInit {
  private readonly organizationService = inject(OrganizationService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly organizations = signal<OrganizationSummary[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly togglingSlug = signal<string | null>(null);
  readonly togglingLdapSlug = signal<string | null>(null);

  readonly pageSize = ADMIN_PAGE_SIZE;

  ngOnInit(): void {
    void this.load();
  }

  async onPageChange(nextPage: number): Promise<void> {
    this.page.set(nextPage);
    await this.load();
  }

  async onSsoToggle(org: OrganizationSummary, enabled: boolean): Promise<void> {
    this.togglingSlug.set(org.slug);

    try {
      const updated = await this.organizationService.setSsoEnabled(org.slug, enabled);
      this.organizations.update((list) =>
        list.map((item) => (item.slug === updated.slug ? { ...item, ssoEnabled: updated.ssoEnabled } : item)),
      );
      this.toast.success(updated.ssoEnabled ? 'SSO enabled' : 'SSO disabled');
    } catch {
      this.toast.error('Failed to update SSO status');
    } finally {
      this.togglingSlug.set(null);
    }
  }

  async onLdapToggle(org: OrganizationSummary, enabled: boolean): Promise<void> {
    if (enabled && !org.ldapConfigured) {
      this.toast.error('Configure LDAP in organization settings before enabling.');
      return;
    }

    this.togglingLdapSlug.set(org.slug);

    try {
      const updated = await this.organizationService.setLdapEnabled(org.slug, enabled);
      this.organizations.update((list) =>
        list.map((item) => (item.slug === updated.slug ? { ...item, ldapEnabled: updated.ldapEnabled } : item)),
      );
      this.toast.success(updated.ldapEnabled ? 'LDAP enabled' : 'LDAP disabled');
    } catch {
      this.toast.error('Failed to update LDAP status');
    } finally {
      this.togglingLdapSlug.set(null);
    }
  }

  formatDomains(domains: string[]): string {
    return domains.length ? domains.join(', ') : '—';
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const res = await this.organizationService.list({ page: this.page(), size: this.pageSize });
      this.organizations.set(res.content);
      this.totalPages.set(res.totalPages);
      this.totalElements.set(res.totalElements);
    } catch {
      this.toast.error('Failed to load organizations');
    } finally {
      this.loading.set(false);
    }
  }
}
