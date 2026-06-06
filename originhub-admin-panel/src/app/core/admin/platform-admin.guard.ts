import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { PlatformAdminService } from './platform-admin.service';
import { ToastService } from '../toast/toast.service';

export const platformAdminGuard = async () => {
  const platformAdminService = inject(PlatformAdminService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const allowed = await platformAdminService.verifyAccess();
  if (allowed) {
    return true;
  }

  toast.error('Platform administrator access required.');
  await router.navigate(['/login'], {
    queryParams: { error: 'Platform administrator access required.' },
  });
  return false;
};
