import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { TokenService } from '../services/token.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenService = inject(TokenService);
  const authService = inject(AuthService);

  const isAuthUrl =
    req.url.includes('/api/auth/login') ||
    req.url.includes('/api/admin/auth/login') ||
    req.url.includes('/api/auth/refresh-token');

  if (!isAuthUrl && tokenService.hasStoredCredentials() && !tokenService.hasValidSession()) {
    void authService.logout();
    return next(req);
  }

  const token = tokenService.getAccessToken();

  if (token && !isAuthUrl) {
    const cloned = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
    return next(cloned);
  }

  return next(req);
};
