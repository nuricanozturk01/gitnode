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

import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { TokenService } from '../../auth/services/token.service';
import { CollaboratorService } from '../../collaborator/collaborator.service';
import type { CollaboratorPermission } from '../../../domain/collaborator/collaborator.model';

const SETTINGS_PERMISSIONS: CollaboratorPermission[] = ['SETTINGS_READ', 'SETTINGS_WRITE', 'ADMIN'];

export const repoSettingsGuard = async (route: ActivatedRouteSnapshot): Promise<boolean> => {
  const tokenService = inject(TokenService);
  const collaboratorService = inject(CollaboratorService);
  const router = inject(Router);

  const owner = route.parent?.paramMap.get('owner') ?? null;
  const repo = route.parent?.paramMap.get('repo') ?? null;
  const username = tokenService.getUsername();

  if (!username) {
    router.navigate(['/login']);
    return false;
  }

  // Repo owner always has full access to settings.
  const isOwner = !!(owner && owner.toLowerCase() === username.toLowerCase());
  if (isOwner) {
    return true;
  }

  // Not the owner — check if the user is an accepted collaborator with settings permissions.
  if (owner && repo) {
    try {
      const invitation = await collaboratorService.getMyInvitation(owner, repo);
      if (invitation.status === 'ACCEPTED' && invitation.permissions.some((p) => SETTINGS_PERMISSIONS.includes(p))) {
        return true;
      }
    } catch {
      // No invitation or request failed — treat as no access.
    }
  }

  // Access denied: redirect to repo root (or home if params unavailable).
  router.navigate(owner && repo ? ['/', owner, repo] : ['/']);
  return false;
};
