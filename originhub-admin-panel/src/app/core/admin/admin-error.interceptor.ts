import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/services/auth.service';
import { PlatformAdminService } from './platform-admin.service';
import { ToastService } from '../toast/toast.service';

export const adminErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);
  const platformAdminService = inject(PlatformAdminService);
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse)) {
        return throwError(() => err);
      }

      const isAdminApi = req.url.includes('/api/admin/');

      if (err.status === 403 && isAdminApi) {
        platformAdminService.reset();
        platformAdminService.verified.set(false);
        toast.error('Platform administrator access required.');
        void authService.logout().then(() => {
          void router.navigate(['/login'], {
            queryParams: { error: 'Platform administrator access required.' },
          });
        });
      }

      return throwError(() => err);
    }),
  );
};
