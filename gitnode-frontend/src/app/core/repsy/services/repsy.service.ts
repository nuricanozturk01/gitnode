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

interface RepsyCredentials {
  username: string;
  passwordHash: string;
}

interface RepsyLoginInfo {
  username: string;
  token: string;
  refreshToken: string;
}

interface RepsyRestResponse<T> {
  data: T;
}

@Injectable({ providedIn: 'root' })
export class RepsyService {
  private readonly http = inject(HttpClient);

  async openDashboard(): Promise<void> {
    try {
      const credentials = await firstValueFrom(
        this.http.get<RepsyCredentials>(`${environment.apiUrl}/api/auth/repsy-credentials`),
      );

      const response = await firstValueFrom(
        this.http.post<RepsyRestResponse<RepsyLoginInfo>>(`${environment.repsyApiUrl}/api/auth/login`, {
          username: credentials.username,
          password: credentials.passwordHash,
        }),
      );

      const loginInfo = response.data;

      const ssoUrl = new URL(`${environment.repsyUrl}/sso`);
      ssoUrl.searchParams.set('username', loginInfo.username);
      ssoUrl.searchParams.set('token', loginInfo.token);
      ssoUrl.searchParams.set('refreshToken', loginInfo.refreshToken);

      window.open(ssoUrl.toString(), '_blank', 'noopener,noreferrer');
    } catch {
      window.open(environment.repsyUrl, '_blank', 'noopener,noreferrer');
    }
  }
}
