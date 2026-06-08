import { Component, ChangeDetectionStrategy, computed, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { AdminAccountService } from '../../core/account/admin-account.service';
import { TokenService } from '../../core/auth/services/token.service';
import type { AdminAccountProfile } from '../../core/admin/admin.models';
import { ToastService } from '../../core/toast/toast.service';
import { apiErrorMessage } from '../../shared/utils/api-error';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const newPassword = group.get('newPassword')?.value;
  const confirmPassword = group.get('confirmPassword')?.value;
  if (!newPassword || !confirmPassword) {
    return null;
  }
  return newPassword === confirmPassword ? null : { passwordMismatch: true };
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-account',
  standalone: true,
  imports: [ReactiveFormsModule, LucideAngularModule],
  templateUrl: './account.page.html',
})
export class AccountPage implements OnInit {
  private readonly accountService = inject(AdminAccountService);
  private readonly tokenService = inject(TokenService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(true);
  readonly savingDisplayName = signal(false);
  readonly savingPassword = signal(false);
  readonly profile = signal<AdminAccountProfile | null>(null);
  readonly profileError = signal<string | null>(null);
  readonly displayNameError = signal<string | null>(null);
  readonly passwordError = signal<string | null>(null);

  readonly displayNameForm = this.fb.nonNullable.group({
    displayName: ['', [Validators.maxLength(100)]],
  });

  readonly passwordForm = this.fb.nonNullable.group(
    {
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(128)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  readonly sessionSummary = computed(() => {
    const accessExpiresAt = this.tokenService.getAccessExpiresAt();
    const refreshExpiresAt = this.tokenService.getRefreshExpiresAt();
    return {
      username: this.tokenService.getUsername(),
      accessExpiresAt,
      refreshExpiresAt,
    };
  });

  ngOnInit(): void {
    void this.loadProfile();
  }

  formatDateTime(value: string | number | null | undefined): string {
    if (value == null) return '—';
    const date = typeof value === 'number' ? new Date(value) : new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  async saveDisplayName(): Promise<void> {
    if (this.displayNameForm.invalid) {
      this.displayNameForm.markAllAsTouched();
      return;
    }

    this.savingDisplayName.set(true);
    this.displayNameError.set(null);

    try {
      const displayName = this.displayNameForm.controls.displayName.value.trim();
      const updated = await this.accountService.updateDisplayName(displayName);
      this.profile.set(updated);
      this.toast.success('Display name updated');
    } catch (e) {
      const msg = apiErrorMessage(e, 'Failed to update display name');
      this.displayNameError.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingDisplayName.set(false);
    }
  }

  async savePassword(): Promise<void> {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    this.savingPassword.set(true);
    this.passwordError.set(null);

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();

    try {
      await this.accountService.changePassword(currentPassword, newPassword);
      this.passwordForm.reset();
      this.toast.success('Password updated');
    } catch (e) {
      const msg = this.passwordChangeErrorMessage(e);
      this.passwordError.set(msg);
      this.toast.error(msg);
    } finally {
      this.savingPassword.set(false);
    }
  }

  private passwordChangeErrorMessage(error: unknown): string {
    const msg = apiErrorMessage(error, 'Failed to update password');
    return msg === 'wrongPassword' || msg === 'Invalid username or password.' ? 'Current password is incorrect.' : msg;
  }

  private async loadProfile(): Promise<void> {
    this.loading.set(true);
    this.profileError.set(null);

    try {
      const profile = await this.accountService.getProfile();
      this.profile.set(profile);
      this.displayNameForm.patchValue({
        displayName: profile.displayName ?? '',
      });
    } catch (e) {
      const msg = apiErrorMessage(e, 'Failed to load account');
      this.profileError.set(msg);
      this.toast.error(msg);
    } finally {
      this.loading.set(false);
    }
  }
}
