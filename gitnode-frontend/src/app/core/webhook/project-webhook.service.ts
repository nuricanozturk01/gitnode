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
export class ProjectWebhookService {
  private readonly http = inject(HttpClient);

  private url(owner: string, projectCode: string): string {
    return `${environment.apiUrl}/api/${owner}/projects/${projectCode}/settings/webhooks`;
  }

  list(owner: string, projectCode: string): Promise<WebhookInfo[]> {
    return firstValueFrom(this.http.get<WebhookInfo[]>(this.url(owner, projectCode)));
  }

  create(owner: string, projectCode: string, form: WebhookForm): Promise<WebhookInfo> {
    return firstValueFrom(this.http.post<WebhookInfo>(this.url(owner, projectCode), form));
  }

  update(owner: string, projectCode: string, id: string, form: WebhookUpdateForm): Promise<WebhookInfo> {
    return firstValueFrom(this.http.patch<WebhookInfo>(`${this.url(owner, projectCode)}/${id}`, form));
  }

  delete(owner: string, projectCode: string, id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.url(owner, projectCode)}/${id}`));
  }
}
