import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { LoginForm, TokenResponse } from '../../organization/organization.models';
import type { AdminLoginResponse } from '../../admin/admin.models';
import { PlatformAdminService } from '../../admin/platform-admin.service';
import { TokenService } from './token.service';

export class PlatformAdminRequiredError extends Error {
  constructor(message = 'Platform administrator access required.') {
    super(message);
    this.name = 'PlatformAdminRequiredError';
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokenService = inject(TokenService);
  private readonly platformAdminService = inject(PlatformAdminService);

  private readonly api = environment.apiUrl;
  private logoutTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.initSession();
    this.bindVisibilityRecheck();
  }

  private initSession(): void {
    if (!this.tokenService.hasStoredCredentials()) return;
    if (!this.tokenService.hasValidSession()) {
      void this.logout();
      return;
    }
    this.scheduleAutoLogout();
  }

  private bindVisibilityRecheck(): void {
    if (typeof document === 'undefined') return;
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState !== 'visible') return;
      if (this.tokenService.hasStoredCredentials() && !this.tokenService.hasValidSession()) {
        void this.logout();
      }
    });
  }

  private scheduleAutoLogout(): void {
    this.cancelAutoLogout();
    const refreshExpiresAt = this.tokenService.getRefreshExpiresAt();
    if (!refreshExpiresAt) {
      if (this.tokenService.hasStoredCredentials()) {
        void this.logout();
      }
      return;
    }
    const delay = refreshExpiresAt - Date.now();
    if (delay <= 0) {
      void this.logout();
      return;
    }
    this.logoutTimer = setTimeout(() => void this.logout(), delay);
  }

  private cancelAutoLogout(): void {
    if (this.logoutTimer !== null) {
      clearTimeout(this.logoutTimer);
      this.logoutTimer = null;
    }
  }

  async login(form: LoginForm): Promise<TokenResponse> {
    this.platformAdminService.reset();

    try {
      return await this.loginViaAdminEndpoint(form);
    } catch (e) {
      if (this.isAdminEndpointUnavailable(e)) {
        return this.loginViaStandardEndpoint(form);
      }
      throw e;
    }
  }

  private async loginViaAdminEndpoint(form: LoginForm): Promise<TokenResponse> {
    const res = await firstValueFrom(this.http.post<AdminLoginResponse>(`${this.api}/api/admin/auth/login`, form));

    if (res.platformAdmin === false) {
      throw new PlatformAdminRequiredError();
    }

    const tokens = this.toTokenResponse(res);
    this.tokenService.saveTokens(tokens);
    this.platformAdminService.verified.set(true);
    this.scheduleAutoLogout();
    return tokens;
  }

  private async loginViaStandardEndpoint(form: LoginForm): Promise<TokenResponse> {
    const res = await firstValueFrom(this.http.post<TokenResponse>(`${this.api}/api/auth/login`, form));
    this.tokenService.saveTokens(res);
    this.scheduleAutoLogout();
    return res;
  }

  private isAdminEndpointUnavailable(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 404 || error.status === 405);
  }

  private toTokenResponse(res: AdminLoginResponse): TokenResponse {
    return {
      token: res.token,
      refreshToken: res.refreshToken,
      expiresIn: res.expiresIn,
      refreshExpiresIn: res.refreshExpiresIn,
      username: res.username,
    };
  }

  async logout(): Promise<void> {
    this.cancelAutoLogout();
    this.tokenService.clearTokens();
    this.platformAdminService.reset();
    await this.router.navigate(['/login']);
  }

  async refreshToken(): Promise<TokenResponse> {
    const refresh = this.tokenService.getRefreshToken();
    if (!refresh) {
      throw new Error('No refresh token');
    }
    if (this.tokenService.isRefreshExpired()) {
      await this.logout();
      throw new Error('Refresh token expired');
    }

    const res = await firstValueFrom(
      this.http.post<TokenResponse>(`${this.api}/api/auth/refresh-token`, { refreshToken: refresh }),
    );
    this.tokenService.saveTokens(res);
    this.scheduleAutoLogout();
    return res;
  }
}
