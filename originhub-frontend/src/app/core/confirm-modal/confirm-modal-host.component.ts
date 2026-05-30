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

import { Component, inject } from '@angular/core';
import { ConfirmModalComponent } from '../../shared/components/confirm-modal/confirm-modal.component';
import { ConfirmModalService } from './confirm-modal.service';

@Component({
  selector: 'app-confirm-modal-host',
  standalone: true,
  imports: [ConfirmModalComponent],
  template: `
    <app-confirm-modal
      [visible]="confirmModal.visible()"
      [title]="confirmModal.title()"
      [message]="confirmModal.message()"
      [confirmLabel]="confirmModal.confirmLabel()"
      [cancelLabel]="confirmModal.cancelLabel()"
      [variant]="confirmModal.variant()"
      (confirm)="confirmModal.onConfirm()"
      (canceled)="confirmModal.onCancel()"
    />
  `,
})
export class ConfirmModalHostComponent {
  readonly confirmModal = inject(ConfirmModalService);
}
