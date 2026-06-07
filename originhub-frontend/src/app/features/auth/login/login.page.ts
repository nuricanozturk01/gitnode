///
/// Copyright 2026 the original author or authors.
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///      https://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Component, ChangeDetectionStrategy, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/services/auth.service';
import { ToastService } from '../../../core/toast/toast.service';
import { authErrorMessage, authQueryErrorMessage } from '../../../shared/utils/api-error.utils';

type LoginMode = 'standard' | 'ldap' | 'saml';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule],
  templateUrl: './login.page.html',
  styleUrl: './login.page.css',
})
export class LoginPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly router: Router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly loginMode = signal<LoginMode>('standard');

  readonly form = this.fb.nonNullable.group({
    usernameOrEmail: ['', [Validators.required]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  readonly ldapForm = this.fb.nonNullable.group({
    workEmail: ['', [Validators.required, Validators.email]],
    username: ['', [Validators.required, Validators.minLength(2)]],
    password: ['', [Validators.required]],
  });

  readonly samlForm = this.fb.nonNullable.group({
    workEmail: ['', [Validators.required, Validators.email]],
  });

  ngOnInit() {
    const mode = this.route.snapshot.queryParamMap.get('mode');
    if (mode === 'ldap' || mode === 'saml') {
      this.loginMode.set(mode);
    }

    const loginError = authQueryErrorMessage(this.route.snapshot.queryParamMap.get('error'));
    if (loginError) {
      this.error.set(loginError);
      this.toast.error(loginError);
      return;
    }

    const token = this.route.snapshot.queryParamMap.get('token');
    const refreshToken = this.route.snapshot.queryParamMap.get('refresh_token');
    const username = this.route.snapshot.queryParamMap.get('username');
    const expiresIn = this.route.snapshot.queryParamMap.get('expires_in');
    const refreshExpiresIn = this.route.snapshot.queryParamMap.get('refresh_expires_in');

    if (token && refreshToken && username) {
      this.authService.loginOauth(
        token,
        refreshToken,
        username,
        expiresIn ? parseInt(expiresIn, 10) : undefined,
        refreshExpiresIn ? parseInt(refreshExpiresIn, 10) : undefined,
      );
      this.toast.success('Login successful');
      void this.router.navigate(['/dashboard']);
    }
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.authService.login(this.form.getRawValue());
      this.toast.success('Login successful');
      this.router.navigate(['/dashboard']);
    } catch (e) {
      const msg = authErrorMessage(e, 'standard', 'Login failed');
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.loading.set(false);
    }
  }

  async onLdapSubmit(): Promise<void> {
    if (this.ldapForm.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const raw = this.ldapForm.getRawValue();
      await this.authService.discoverLdap(raw.workEmail.trim());
      await this.authService.loginLdap({
        email: raw.workEmail.trim(),
        username: raw.username.trim(),
        password: raw.password,
      });
      this.toast.success('Login successful');
      this.router.navigate(['/dashboard']);
    } catch (e) {
      const msg = authErrorMessage(e, 'ldap', 'LDAP authentication failed');
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.loading.set(false);
    }
  }

  switchToLdap(): void {
    this.error.set(null);
    this.ldapForm.reset();
    this.loginMode.set('ldap');
  }

  switchToSaml(): void {
    this.error.set(null);
    this.samlForm.reset();
    this.loginMode.set('saml');
  }

  switchToStandard(): void {
    this.error.set(null);
    this.loginMode.set('standard');
  }

  async onSamlSubmit(): Promise<void> {
    if (this.samlForm.invalid) {
      this.samlForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      const { workEmail } = this.samlForm.getRawValue();
      const discovery = await this.authService.discoverSaml(workEmail.trim());
      window.location.href = `${environment.apiUrl}${discovery.redirectUrl}`;
    } catch (e) {
      const msg = authErrorMessage(e, 'saml', 'SSO sign-in failed');
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.loading.set(false);
    }
  }

  loginWithGithub(): void {
    window.location.href = `${environment.apiUrl}/oauth2/authorization/github`;
  }

  loginWithGitlab(): void {
    window.location.href = `${environment.apiUrl}/oauth2/authorization/gitlab`;
  }

  loginWithGoogle(): void {
    window.location.href = `${environment.apiUrl}/oauth2/authorization/google`;
  }

  loginWithSaml(): void {
    if (this.loginMode() !== 'saml') {
      this.switchToSaml();
      return;
    }
    void this.onSamlSubmit();
  }
}
