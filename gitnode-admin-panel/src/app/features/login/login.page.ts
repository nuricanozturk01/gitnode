import { Component, ChangeDetectionStrategy, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService, PlatformAdminRequiredError } from '../../core/auth/services/auth.service';
import { PlatformAdminService } from '../../core/admin/platform-admin.service';
import { ToastService } from '../../core/toast/toast.service';
import { apiErrorMessage } from '../../shared/utils/api-error';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, LucideAngularModule],
  templateUrl: './login.page.html',
})
export class LoginPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly platformAdminService = inject(PlatformAdminService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    usernameOrEmail: ['', [Validators.required]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  ngOnInit(): void {
    const queryError = this.route.snapshot.queryParamMap.get('error');
    if (queryError) {
      this.error.set(queryError);
    }
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.authService.login(this.form.getRawValue());

      if (this.platformAdminService.verified() !== true) {
        const allowed = await this.platformAdminService.verifyAccess();
        if (!allowed) {
          await this.rejectNonAdmin();
          return;
        }
      }

      this.toast.success('Login successful');
      await this.router.navigate(['/dashboard']);
    } catch (e) {
      if (e instanceof PlatformAdminRequiredError) {
        await this.rejectNonAdmin(e.message);
        return;
      }
      const msg = apiErrorMessage(e, 'Login failed');
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.loading.set(false);
    }
  }

  private async rejectNonAdmin(message = 'Platform administrator access required.'): Promise<void> {
    this.error.set(message);
    this.toast.error(message);
    await this.authService.logout();
  }
}
