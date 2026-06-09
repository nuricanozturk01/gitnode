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

import { HttpInterceptorFn, HttpStatusCode } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { TokenService } from '../services/token.service';

let refreshInProgress: Promise<unknown> | null = null;

/** Set on the retried request so a second 401 does not loop refresh. */
const AUTH_RETRY_HEADER = 'X-Auth-Retry';

export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const tokenService = inject(TokenService);

  return next(req).pipe(
    catchError((error) => {
      const isAuthUrl =
        req.url.includes('/auth/login') ||
        req.url.includes('/auth/register') ||
        req.url.includes('/auth/refresh-token');

      if (error.status === HttpStatusCode.Unauthorized && !isAuthUrl) {
        if (req.headers.has(AUTH_RETRY_HEADER)) {
          if (tokenService.hasStoredCredentials()) {
            void authService.logout();
          }
          return throwError(() => error);
        }

        if (!tokenService.hasValidSession()) {
          if (tokenService.hasStoredCredentials()) {
            void authService.logout();
          }
          return throwError(() => error);
        }

        const doRefresh = () => {
          if (!refreshInProgress) {
            refreshInProgress = authService.refreshToken().finally(() => {
              refreshInProgress = null;
            });
          }
          return refreshInProgress;
        };

        return from(doRefresh()).pipe(
          switchMap((tokens) => {
            const t = tokens as { token: string };
            const retried = req.clone({
              setHeaders: {
                Authorization: `Bearer ${t.token}`,
                [AUTH_RETRY_HEADER]: '1',
              },
            });
            return next(retried);
          }),
          catchError(() => {
            void authService.logout();
            return throwError(() => error);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
