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
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { authGuard } from './auth.guard';
import { TokenService } from '../services/token.service';
import { AuthService } from '../services/auth.service';
import { UserService } from '../../user/services/user.service';

describe('authGuard', () => {
  let tokenService: TokenService;
  let routerNavigate: jasmine.Spy;

  beforeEach(() => {
    localStorage.clear();
    routerNavigate = jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true));

    TestBed.configureTestingModule({
      providers: [
        { provide: HttpClient, useValue: { post: jasmine.createSpy('post') } },
        { provide: Router, useValue: { navigate: routerNavigate } },
        { provide: UserService, useValue: { invalidateMe: jasmine.createSpy('invalidateMe') } },
      ],
    });
    tokenService = TestBed.inject(TokenService);
    TestBed.inject(AuthService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('allows navigation when session is valid', () => {
    tokenService.saveTokens({
      token: 'a',
      refreshToken: 'r',
      expiresIn: 3600,
      refreshExpiresIn: 7200,
    });
    expect(TestBed.runInInjectionContext(() => authGuard())).toBe(true);
  });

  it('redirects to login when there are no stored credentials', () => {
    expect(TestBed.runInInjectionContext(() => authGuard())).toBe(false);
    expect(routerNavigate).toHaveBeenCalledWith(['/login']);
  });

  it('logs out when credentials exist but session is invalid', () => {
    localStorage.setItem('refresh_token', 'stale');
    localStorage.setItem('refresh_expires_at', String(Date.now() - 1000));
    routerNavigate.calls.reset();

    expect(TestBed.runInInjectionContext(() => authGuard())).toBe(false);
    expect(routerNavigate).toHaveBeenCalledWith(['/login']);
  });
});
