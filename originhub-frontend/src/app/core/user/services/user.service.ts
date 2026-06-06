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
import { environment } from '../../../../environments/environment';
import type { User } from '../../../domain/auth/models/user.model';
import type { UserContributionsResponse } from '../../../domain/profile/models/contribution.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  private readonly api = `${environment.apiUrl}/api/users`;
  private meCache: Promise<User> | null = null;
  private meSnapshot: User | null = null;

  updateUsername(username: string): Promise<User> {
    return this.patchMeAndCache(`${this.api}/me`, { username });
  }

  getMe(): Promise<User> {
    if (this.meSnapshot) return Promise.resolve(this.meSnapshot);
    if (!this.meCache) {
      this.meCache = firstValueFrom(this.http.get<User>(`${this.api}/me`))
        .then((user) => {
          this.meSnapshot = user;
          return user;
        })
        .catch((err) => {
          this.meCache = null;
          throw err;
        });
    }
    return this.meCache;
  }

  /** Clears cached profile (call on logout or after account changes). */
  invalidateMe(): void {
    this.meCache = null;
    this.meSnapshot = null;
  }

  updateDisplayName(displayName: string): Promise<User> {
    return this.patchMeAndCache(`${this.api}/me/display-name`, { displayName });
  }

  changePassword(currentPassword: string, newPassword: string): Promise<void> {
    return firstValueFrom(
      this.http.patch<void>(`${this.api}/me/password`, {
        currentPassword,
        newPassword,
      }),
    );
  }

  deleteAccount(): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.api}/me`)).then(() => {
      this.invalidateMe();
    });
  }

  getPublicProfile(username: string): Promise<UserPublicProfile> {
    return firstValueFrom(this.http.get<UserPublicProfile>(`${this.api}/${encodeURIComponent(username)}`));
  }

  getContributions(username: string): Promise<UserContributionsResponse> {
    return firstValueFrom(
      this.http.get<UserContributionsResponse>(`${this.api}/${encodeURIComponent(username)}/contributions`),
    );
  }

  updateProfile(form: UpdateProfileForm): Promise<User> {
    return this.patchMeAndCache(`${this.api}/me/profile`, form);
  }

  private patchMeAndCache(url: string, body: unknown): Promise<User> {
    return firstValueFrom(this.http.patch<User>(url, body)).then((user) => {
      this.meSnapshot = user;
      this.meCache = Promise.resolve(user);
      return user;
    });
  }
}

export interface UserPublicProfile {
  username: string;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
  website: string | null;
  location: string | null;
  profileReadme: string | null;
}

export interface UpdateProfileForm {
  bio?: string | null;
  website?: string | null;
  location?: string | null;
  profileReadme?: string | null;
}
