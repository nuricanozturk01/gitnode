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

import { TestBed } from '@angular/core/testing';
import { TokenService } from './token.service';
import type { TokenResponse } from '../../../domain/auth/ports/auth.port';

const ACCESS_KEY = 'token';
const REFRESH_KEY = 'refresh_token';
const EXPIRES_KEY = 'expires_at';
const REFRESH_EXPIRES_KEY = 'refresh_expires_at';

function validTokens(overrides: Partial<TokenResponse> = {}): TokenResponse {
  return {
    token: 'access-token',
    refreshToken: 'refresh-token',
    expiresIn: 3600,
    refreshExpiresIn: 7200,
    username: 'alice',
    ...overrides,
  };
}

describe('TokenService', () => {
  let service: TokenService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(TokenService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('hasValidSession is false when refresh token is missing', () => {
    expect(service.hasValidSession()).toBe(false);
  });

  it('hasValidSession is false when refresh token is expired', () => {
    localStorage.setItem(REFRESH_KEY, 'refresh');
    localStorage.setItem(REFRESH_EXPIRES_KEY, String(Date.now() - 1000));
    expect(service.hasValidSession()).toBe(false);
  });

  it('hasValidSession is true when refresh token exists and is not expired', () => {
    service.saveTokens(validTokens());
    expect(service.hasValidSession()).toBe(true);
  });

  it('hasStoredCredentials reflects access or refresh in storage', () => {
    expect(service.hasStoredCredentials()).toBe(false);
    localStorage.setItem(ACCESS_KEY, 'a');
    expect(service.hasStoredCredentials()).toBe(true);
    localStorage.clear();
    localStorage.setItem(REFRESH_KEY, 'r');
    expect(service.hasStoredCredentials()).toBe(true);
  });

  it('isLoggedIn updates when tokens are saved or cleared', () => {
    expect(service.isLoggedIn()).toBe(false);
    service.saveTokens(validTokens());
    expect(service.isLoggedIn()).toBe(true);
    service.clearTokens();
    expect(service.isLoggedIn()).toBe(false);
  });

  it('saveTokens applies default TTLs when expiresIn and refreshExpiresIn are omitted', () => {
    const now = Date.now();
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(now));

    service.saveTokens({ token: 'a', refreshToken: 'r' });

    const accessExpires = parseInt(localStorage.getItem(EXPIRES_KEY)!, 10);
    const refreshExpires = parseInt(localStorage.getItem(REFRESH_EXPIRES_KEY)!, 10);
    expect(accessExpires).toBe(now + 24 * 3600 * 1000);
    expect(refreshExpires).toBe(now + 48 * 3600 * 1000);

    jasmine.clock().uninstall();
  });

  it('isExpired and isRefreshExpired reflect stored expiry timestamps', () => {
    service.saveTokens(validTokens({ expiresIn: 1, refreshExpiresIn: 1 }));
    expect(service.isExpired()).toBe(false);
    expect(service.isRefreshExpired()).toBe(false);

    localStorage.setItem(EXPIRES_KEY, String(Date.now() - 1));
    localStorage.setItem(REFRESH_EXPIRES_KEY, String(Date.now() - 1));
    expect(service.isExpired()).toBe(true);
    expect(service.isRefreshExpired()).toBe(true);
  });

  it('clearTokens removes all session keys', () => {
    service.saveTokens(validTokens());
    service.clearTokens();
    expect(localStorage.getItem(ACCESS_KEY)).toBeNull();
    expect(localStorage.getItem(REFRESH_KEY)).toBeNull();
    expect(localStorage.getItem(EXPIRES_KEY)).toBeNull();
    expect(localStorage.getItem(REFRESH_EXPIRES_KEY)).toBeNull();
    expect(localStorage.getItem('username')).toBeNull();
  });
});
