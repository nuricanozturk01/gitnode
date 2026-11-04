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

import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { TeamMe, TeamMember, TeamMemberStatus, TeamPage, TeamRole } from '../../domain/team/team.model';

@Injectable({ providedIn: 'root' })
export class TeamService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/api/team`;

  readonly me = signal<TeamMe | null>(null);

  async loadMe(): Promise<void> {
    try {
      this.me.set(await firstValueFrom(this.http.get<TeamMe>(`${this.api}/me`)));
    } catch {
      this.me.set(null);
    }
  }

  members(status: TeamMemberStatus, page = 0, size = 20): Promise<TeamPage> {
    return firstValueFrom(this.http.get<TeamPage>(`${this.api}/members`, { params: { status, page, size } }));
  }

  invite(username: string, role: TeamRole): Promise<TeamMember> {
    return firstValueFrom(this.http.post<TeamMember>(`${this.api}/invitations`, { username, role }));
  }

  cancelInvitation(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.api}/invitations/${id}`));
  }

  changeRole(id: string, role: TeamRole): Promise<TeamMember> {
    return firstValueFrom(this.http.patch<TeamMember>(`${this.api}/members/${id}/role`, { role }));
  }

  remove(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.api}/members/${id}`));
  }

  memberships(page = 0, size = 20): Promise<TeamPage> {
    return firstValueFrom(this.http.get<TeamPage>(`${this.api}/memberships`, { params: { page, size } }));
  }

  leave(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.api}/memberships/${id}`));
  }

  myInvitations(page = 0, size = 20): Promise<TeamPage> {
    return firstValueFrom(this.http.get<TeamPage>(`${this.api}/invitations/mine`, { params: { page, size } }));
  }

  accept(id: string): Promise<TeamMember> {
    return firstValueFrom(this.http.post<TeamMember>(`${this.api}/invitations/${id}/accept`, {}));
  }

  decline(id: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.api}/invitations/${id}/decline`, {}));
  }
}
