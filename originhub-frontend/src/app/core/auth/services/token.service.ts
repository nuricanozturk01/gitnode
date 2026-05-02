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

import { Injectable, signal, computed } from '@angular/core';
import type { TokenResponse } from '../../../domain/auth/ports/auth.port';

const ACCESS_KEY = 'token';
const REFRESH_KEY = 'refresh_token';
const EXPIRES_KEY = 'expires_at';
const REFRESH_EXPIRES_KEY = 'refresh_expires_at';
const USERNAME_KEY = 'username';

const ACCESS_TOKEN_TTL_SECONDS = 1800; // 30 minutes
const REFRESH_TOKEN_TTL_SECONDS = 3600; // 1 hour

@Injectable({ providedIn: 'root' })
export class TokenService {
  private readonly _loggedIn = signal(this._hasValidSession());

  readonly isLoggedIn = computed(() => this._loggedIn());

  private _hasValidSession(): boolean {
    if (!localStorage.getItem(ACCESS_KEY)) return false;
    const refreshExpires = localStorage.getItem(REFRESH_EXPIRES_KEY);
    return !!refreshExpires && Date.now() < parseInt(refreshExpires, 10);
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

  saveTokens(tokens: TokenResponse): void {
    const expiresIn = tokens.expiresIn ?? ACCESS_TOKEN_TTL_SECONDS;
    const expiresAt = Date.now() + expiresIn * 1000;
    const refreshExpiresAt = Date.now() + REFRESH_TOKEN_TTL_SECONDS * 1000;
    localStorage.setItem(ACCESS_KEY, tokens.token);
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    localStorage.setItem(EXPIRES_KEY, String(expiresAt));
    localStorage.setItem(REFRESH_EXPIRES_KEY, String(refreshExpiresAt));
    if (tokens.username) {
      localStorage.setItem(USERNAME_KEY, tokens.username);
    }
    this._loggedIn.set(true);
  }

  clearTokens(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(EXPIRES_KEY);
    localStorage.removeItem(REFRESH_EXPIRES_KEY);
    localStorage.removeItem(USERNAME_KEY);
    this._loggedIn.set(false);
  }

  isExpired(): boolean {
    const expires = localStorage.getItem(EXPIRES_KEY);
    return !expires || Date.now() >= parseInt(expires, 10);
  }

  isRefreshExpired(): boolean {
    const expires = localStorage.getItem(REFRESH_EXPIRES_KEY);
    return !expires || Date.now() >= parseInt(expires, 10);
  }
}
