import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenService } from '../../auth/services/token.service';
import { CollaboratorService } from '../../collaborator/collaborator.service';
import type { CollaboratorPermission } from '../../../domain/collaborator/collaborator.model';

export const repoPermissionGuard: CanActivateFn = async (route) => {
  const tokenService = inject(TokenService);
  const collaborators = inject(CollaboratorService);
  const router = inject(Router);
  const owner = route.parent?.paramMap.get('owner');
  const repo = route.parent?.paramMap.get('repo');
  const username = tokenService.getUsername();
  if (!username) return router.createUrlTree(['/login']);
  if (owner && username.toLowerCase() === owner.toLowerCase()) return true;
  if (owner && repo) {
    try {
      const invitation = await collaborators.getMyInvitation(owner, repo);
      const permission = route.data['permission'] as CollaboratorPermission;
      if (invitation.status === 'ACCEPTED'
        && (invitation.permissions.includes(permission) || invitation.permissions.includes('ADMIN'))) return true;
    } catch {
      // Treat missing or inaccessible invitations as no permission.
    }
  }
  return router.createUrlTree(owner && repo ? ['/', owner, repo] : ['/']);
};
