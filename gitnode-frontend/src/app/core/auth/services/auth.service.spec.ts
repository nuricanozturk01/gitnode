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
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from './auth.service';
import { TokenService } from './token.service';
import { UserService } from '../../user/services/user.service';

describe('AuthService', () => {
  let authService: AuthService;
  let tokenService: TokenService;
  let routerNavigate: jasmine.Spy;
  let invalidateMe: jasmine.Spy;
  let httpPost: jasmine.Spy;

  beforeEach(() => {
    localStorage.clear();
    routerNavigate = jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true));
    invalidateMe = jasmine.createSpy('invalidateMe');
    httpPost = jasmine.createSpy('post');

    TestBed.configureTestingModule({
      providers: [
        { provide: HttpClient, useValue: { post: httpPost } },
        { provide: Router, useValue: { navigate: routerNavigate } },
        { provide: UserService, useValue: { invalidateMe } },
      ],
    });

    tokenService = TestBed.inject(TokenService);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    localStorage.clear();
    if (jasmine.clock) {
      try {
        jasmine.clock().uninstall();
      } catch {
        /* not installed */
      }
    }
  });

  it('initSession logs out when stored credentials have an expired refresh token', () => {
    localStorage.clear();
    localStorage.setItem('refresh_token', 'stale');
    localStorage.setItem('refresh_expires_at', String(Date.now() - 1000));
    routerNavigate.calls.reset();
    invalidateMe.calls.reset();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: HttpClient, useValue: { post: jasmine.createSpy('post') } },
        { provide: Router, useValue: { navigate: routerNavigate } },
        { provide: UserService, useValue: { invalidateMe } },
      ],
    });
    TestBed.inject(AuthService);

    expect(routerNavigate).toHaveBeenCalledWith(['/login']);
    expect(invalidateMe).toHaveBeenCalled();
    expect(localStorage.getItem('refresh_token')).toBeNull();
  });

  it('scheduleAutoLogout calls logout when refresh expiry elapses', () => {
    jasmine.clock().install();
    const now = Date.now();
    jasmine.clock().mockDate(new Date(now));

    authService.loginOauth('access', 'refresh', 'alice', 3600, 2);
    routerNavigate.calls.reset();
    invalidateMe.calls.reset();

    jasmine.clock().tick(2001);

    expect(routerNavigate).toHaveBeenCalledWith(['/login']);
    expect(localStorage.getItem('refresh_token')).toBeNull();
    jasmine.clock().uninstall();
  });

  it('scheduleAutoLogout calls logout immediately when refresh is already expired', () => {
    jasmine.clock().install();
    const now = Date.now();
    jasmine.clock().mockDate(new Date(now));

    tokenService.saveTokens({
      token: 'a',
      refreshToken: 'r',
      expiresIn: 3600,
      refreshExpiresIn: 0,
    });
    routerNavigate.calls.reset();

    authService.loginOauth('access2', 'refresh2', 'bob', 3600, 0);
    jasmine.clock().tick(0);

    expect(routerNavigate).toHaveBeenCalledWith(['/login']);
    jasmine.clock().uninstall();
  });

  it('refreshToken logs out and rejects when refresh token is expired', async () => {
    tokenService.saveTokens({
      token: 'a',
      refreshToken: 'r',
      expiresIn: 3600,
      refreshExpiresIn: 1,
    });
    localStorage.setItem('refresh_expires_at', String(Date.now() - 1000));
    routerNavigate.calls.reset();

    await expectAsync(authService.refreshToken()).toBeRejectedWithError('Refresh token expired');
    expect(routerNavigate).toHaveBeenCalledWith(['/login']);
    expect(localStorage.getItem('refresh_token')).toBeNull();
  });

  it('refreshToken saves new tokens when refresh succeeds', async () => {
    tokenService.saveTokens({
      token: 'old-access',
      refreshToken: 'old-refresh',
      expiresIn: 3600,
      refreshExpiresIn: 7200,
    });
    httpPost.and.returnValue(
      of({
        token: 'new-access',
        refreshToken: 'new-refresh',
        expiresIn: 3600,
        refreshExpiresIn: 7200,
      }),
    );

    const res = await authService.refreshToken();

    expect(res.token).toBe('new-access');
    expect(tokenService.getAccessToken()).toBe('new-access');
    expect(tokenService.getRefreshToken()).toBe('new-refresh');
  });
});
