import { Component, ChangeDetectionStrategy, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule } from 'lucide-angular';
import { OrganizationService } from '../../../core/organization/organization.service';
import type { OrganizationDetail } from '../../../core/organization/organization.models';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import { SsoConfigComponent } from '../sso-config/sso-config.component';
import { LdapConfigComponent } from '../ldap-config/ldap-config.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-organization-detail',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, SsoConfigComponent, LdapConfigComponent],
  templateUrl: './organization-detail.page.html',
})
export class OrganizationDetailPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly organizationService = inject(OrganizationService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly deleting = signal(false);
  readonly organization = signal<OrganizationDetail | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    emailDomains: ['', [Validators.required]],
  });

  ngOnInit(): void {
    void this.load();
  }

  onSsoUpdated(org: OrganizationDetail): void {
    this.organization.set(org);
  }

  onLdapUpdated(org: OrganizationDetail): void {
    this.organization.set(org);
  }

  async onSaveOrg(): Promise<void> {
    const org = this.organization();
    if (!org || this.form.invalid) return;
    this.saving.set(true);
    const raw = this.form.getRawValue();
    const emailDomains = raw.emailDomains
      .split(',')
      .map((d) => d.trim().toLowerCase())
      .filter(Boolean);

    try {
      const updated = await this.organizationService.update(org.slug, {
        name: raw.name.trim(),
        emailDomains,
      });
      this.organization.set(updated);
      this.patchForm(updated);
      this.toast.success('Organization updated');
    } catch (e) {
      this.toast.error(this.extractErrorMessage(e, 'Failed to update organization'));
    } finally {
      this.saving.set(false);
    }
  }

  async onDelete(): Promise<void> {
    const org = this.organization();
    if (!org) return;

    const confirmed = await this.confirmModal.confirm(
      'Delete organization',
      `Permanently delete "${org.name}"? This cannot be undone.`,
      { confirmLabel: 'Delete', variant: 'danger' },
    );
    if (!confirmed) return;

    this.deleting.set(true);
    try {
      await this.organizationService.delete(org.slug);
      this.toast.success('Organization deleted');
      await this.router.navigate(['/organizations']);
    } catch (e) {
      this.toast.error(this.extractErrorMessage(e, 'Failed to delete organization'));
    } finally {
      this.deleting.set(false);
    }
  }

  private async load(): Promise<void> {
    const slug = this.route.snapshot.paramMap.get('slug');
    if (!slug) {
      await this.router.navigate(['/organizations']);
      return;
    }

    this.loading.set(true);
    try {
      const org = await this.organizationService.get(slug);
      this.organization.set(org);
      this.patchForm(org);
    } catch {
      this.toast.error('Organization not found');
      await this.router.navigate(['/organizations']);
    } finally {
      this.loading.set(false);
    }
  }

  private patchForm(org: OrganizationDetail): void {
    this.form.reset({
      name: org.name,
      emailDomains: org.emailDomains.join(', '),
    });
  }

  private extractErrorMessage(e: unknown, fallback: string): string {
    if (e instanceof HttpErrorResponse) {
      return e.error?.message ?? e.statusText ?? fallback;
    }
    return (e as Error).message ?? fallback;
  }
}
