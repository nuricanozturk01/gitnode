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
import type { SshKeyInfo } from '../../../domain/ssh/models/ssh-key-info.model';
import type { AddSshKeyForm } from '../../../domain/ssh/models/add-ssh-key-form.model';

@Injectable({ providedIn: 'root' })
export class SshKeyService {
  private readonly http = inject(HttpClient);

  private readonly api = `${environment.apiUrl}/api/user/ssh-keys`;

  listKeys(): Promise<SshKeyInfo[]> {
    return firstValueFrom(this.http.get<SshKeyInfo[]>(this.api));
  }

  addKey(form: AddSshKeyForm): Promise<SshKeyInfo> {
    return firstValueFrom(this.http.post<SshKeyInfo>(this.api, form));
  }

  deleteKey(keyId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.api}/${keyId}`));
  }
}
