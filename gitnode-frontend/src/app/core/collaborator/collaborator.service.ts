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

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type {
  CollaboratorInfo,
  CollaboratorPage,
  InviteLinkResponse,
  InvitationTokenInfo,
  InviteCollaboratorForm,
  UpdateCollaboratorPermissionsForm,
} from '../../domain/collaborator/collaborator.model';

@Injectable({ providedIn: 'root' })
export class CollaboratorService {
  private readonly http = inject(HttpClient);

  private api(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/collaborators`;
  }

  list(owner: string, repo: string, page = 0, size = 20): Promise<CollaboratorPage> {
    return firstValueFrom(this.http.get<CollaboratorPage>(this.api(owner, repo), { params: { page, size } }));
  }

  invite(owner: string, repo: string, form: InviteCollaboratorForm): Promise<CollaboratorInfo> {
    return firstValueFrom(this.http.post<CollaboratorInfo>(this.api(owner, repo), form));
  }

  remove(owner: string, repo: string, username: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.api(owner, repo)}/${username}`));
  }

  updatePermissions(
    owner: string,
    repo: string,
    username: string,
    form: UpdateCollaboratorPermissionsForm,
  ): Promise<CollaboratorInfo> {
    return firstValueFrom(this.http.put<CollaboratorInfo>(`${this.api(owner, repo)}/${username}/permissions`, form));
  }

  getMyInvitation(owner: string, repo: string): Promise<CollaboratorInfo> {
    return firstValueFrom(this.http.get<CollaboratorInfo>(`${this.api(owner, repo)}/invitation`));
  }

  acceptInvitation(owner: string, repo: string): Promise<CollaboratorInfo> {
    return firstValueFrom(this.http.post<CollaboratorInfo>(`${this.api(owner, repo)}/invitation/accept`, {}));
  }

  declineInvitation(owner: string, repo: string): Promise<CollaboratorInfo> {
    return firstValueFrom(this.http.post<CollaboratorInfo>(`${this.api(owner, repo)}/invitation/decline`, {}));
  }

  generateInviteLink(owner: string, repo: string, username: string): Promise<InviteLinkResponse> {
    return firstValueFrom(this.http.post<InviteLinkResponse>(`${this.api(owner, repo)}/${username}/invite-link`, {}));
  }

  getInvitationByToken(token: string): Promise<InvitationTokenInfo> {
    return firstValueFrom(this.http.get<InvitationTokenInfo>(`${environment.apiUrl}/api/invitations/${token}`));
  }

  acceptViaToken(token: string): Promise<CollaboratorInfo> {
    return firstValueFrom(
      this.http.post<CollaboratorInfo>(`${environment.apiUrl}/api/invitations/${token}/accept`, {}),
    );
  }

  sendInvitationEmail(owner: string, repo: string, username: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.api(owner, repo)}/${username}/send-invitation`, {}));
  }

  searchUsers(query: string): Promise<{ username: string; displayName: string | null; avatarUrl: string }[]> {
    return firstValueFrom(
      this.http.get<{ username: string; displayName: string | null; avatarUrl: string }[]>(
        `${environment.apiUrl}/api/users/search`,
        { params: { q: query } },
      ),
    );
  }
}
