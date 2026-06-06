import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { TokenService } from '../services/token.service';

export const authGuard = () => {
  const tokenService = inject(TokenService);
  const authService = inject(AuthService);
  const router = inject(Router);

  if (tokenService.hasValidSession()) {
    return true;
  }

  if (tokenService.hasStoredCredentials()) {
    void authService.logout();
  } else {
    router.navigate(['/login']);
  }
  return false;
};
