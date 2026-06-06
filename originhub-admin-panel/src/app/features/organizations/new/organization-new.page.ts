import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule } from 'lucide-angular';
import { OrganizationService } from '../../../core/organization/organization.service';
import { ToastService } from '../../../core/toast/toast.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-organization-new',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule],
  templateUrl: './organization-new.page.html',
})
export class OrganizationNewPage {
  private readonly fb = inject(FormBuilder);
  private readonly organizationService = inject(OrganizationService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)]],
    emailDomains: ['', [Validators.required]],
  });

  async onSubmit(): Promise<void> {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.error.set(null);
    const raw = this.form.getRawValue();
    const emailDomains = raw.emailDomains
      .split(',')
      .map((d) => d.trim().toLowerCase())
      .filter(Boolean);

    try {
      const org = await this.organizationService.create({
        name: raw.name.trim(),
        slug: raw.slug.trim(),
        emailDomains,
      });
      this.toast.success('Organization created');
      await this.router.navigate(['/organizations', org.slug]);
    } catch (e) {
      const msg = this.extractErrorMessage(e, 'Failed to create organization');
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.saving.set(false);
    }
  }

  slugifyFromName(): void {
    const name = this.form.controls.name.value.trim();
    if (!name || this.form.controls.slug.dirty) return;
    const slug = name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
    this.form.controls.slug.setValue(slug);
  }

  private extractErrorMessage(e: unknown, fallback: string): string {
    if (e instanceof HttpErrorResponse) {
      return e.error?.message ?? e.statusText ?? fallback;
    }
    return (e as Error).message ?? fallback;
  }
}
