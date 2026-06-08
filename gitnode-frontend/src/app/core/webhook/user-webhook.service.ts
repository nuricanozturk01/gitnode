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
import type { WebhookInfo, WebhookForm, WebhookUpdateForm } from '../../domain/webhook/webhook.model';

@Injectable({ providedIn: 'root' })
export class UserWebhookService {
  private readonly http = inject(HttpClient);

  private url(username: string): string {
    return `${environment.apiUrl}/api/users/${username}/settings/webhooks`;
  }

  list(username: string): Promise<WebhookInfo[]> {
    return firstValueFrom(this.http.get<WebhookInfo[]>(this.url(username)));
  }

  create(username: string, form: WebhookForm): Promise<WebhookInfo> {
    return firstValueFrom(this.http.post<WebhookInfo>(this.url(username), form));
  }

  update(username: string, id: string, form: WebhookUpdateForm): Promise<WebhookInfo> {
    return firstValueFrom(this.http.patch<WebhookInfo>(`${this.url(username)}/${id}`, form));
  }

  delete(username: string, id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.url(username)}/${id}`));
  }
}
