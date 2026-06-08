import { Injectable, signal, computed } from '@angular/core';
import type { TokenResponse } from '../../organization/organization.models';

const ACCESS_KEY = 'admin_token';
const REFRESH_KEY = 'admin_refresh_token';
const EXPIRES_KEY = 'admin_expires_at';
const REFRESH_EXPIRES_KEY = 'admin_refresh_expires_at';
const USERNAME_KEY = 'admin_username';

const ACCESS_TOKEN_TTL_SECONDS = 24 * 3600;
const REFRESH_TOKEN_TTL_SECONDS = 48 * 3600;

@Injectable({ providedIn: 'root' })
export class TokenService {
  private readonly sessionVersion = signal(0);

  readonly isLoggedIn = computed(() => {
    this.sessionVersion();
    return this.hasValidSession();
  });

  hasValidSession(): boolean {
    if (!localStorage.getItem(REFRESH_KEY)) return false;
    return !this.isRefreshExpired();
  }

  hasStoredCredentials(): boolean {
    return !!localStorage.getItem(ACCESS_KEY) || !!localStorage.getItem(REFRESH_KEY);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  }

  getUsername(): string | null {
    return localStorage.getItem(USERNAME_KEY);
  }

  getRefreshExpiresAt(): number | null {
    const val = localStorage.getItem(REFRESH_EXPIRES_KEY);
    return val ? parseInt(val, 10) : null;
  }

  getAccessExpiresAt(): number | null {
    const val = localStorage.getItem(EXPIRES_KEY);
    return val ? parseInt(val, 10) : null;
  }

  saveTokens(tokens: TokenResponse): void {
    const expiresIn = tokens.expiresIn ?? ACCESS_TOKEN_TTL_SECONDS;
    const refreshExpiresIn = tokens.refreshExpiresIn ?? REFRESH_TOKEN_TTL_SECONDS;
    const expiresAt = Date.now() + expiresIn * 1000;
    const refreshExpiresAt = Date.now() + refreshExpiresIn * 1000;
    localStorage.setItem(ACCESS_KEY, tokens.token);
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    localStorage.setItem(EXPIRES_KEY, String(expiresAt));
    localStorage.setItem(REFRESH_EXPIRES_KEY, String(refreshExpiresAt));
    if (tokens.username) {
      localStorage.setItem(USERNAME_KEY, tokens.username);
    }
    this.bumpSessionVersion();
  }

  clearTokens(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(EXPIRES_KEY);
    localStorage.removeItem(REFRESH_EXPIRES_KEY);
    localStorage.removeItem(USERNAME_KEY);
    this.bumpSessionVersion();
  }

  private bumpSessionVersion(): void {
    this.sessionVersion.update((n) => n + 1);
  }

  isRefreshExpired(): boolean {
    const expires = localStorage.getItem(REFRESH_EXPIRES_KEY);
    return !expires || Date.now() >= parseInt(expires, 10);
  }
}
