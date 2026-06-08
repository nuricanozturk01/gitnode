import { HttpInterceptorFn, HttpStatusCode } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { TokenService } from '../services/token.service';

let refreshInProgress: Promise<unknown> | null = null;

const AUTH_RETRY_HEADER = 'X-Auth-Retry';

function isAuthEndpoint(url: string): boolean {
  return (
    url.includes('/api/auth/login') || url.includes('/api/admin/auth/login') || url.includes('/api/auth/refresh-token')
  );
}

export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const tokenService = inject(TokenService);

  return next(req).pipe(
    catchError((error) => {
      if (error.status !== HttpStatusCode.Unauthorized || isAuthEndpoint(req.url)) {
        return throwError(() => error);
      }

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
    }),
  );
};
