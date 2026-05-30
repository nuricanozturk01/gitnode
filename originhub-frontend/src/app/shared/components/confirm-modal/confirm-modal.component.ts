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

import { Component, input, output, computed } from '@angular/core';

export type ConfirmVariant = 'danger' | 'primary';

@Component({
  selector: 'app-confirm-modal',
  standalone: true,
  template: `
    <div class="modal" [class.modal-open]="visible()">
      <div class="modal-box">
        <h3 class="text-base-content text-lg font-bold">{{ title() }}</h3>
        @if (message()) {
          <p class="text-base-content/80 py-4">{{ message() }}</p>
        }
        <div class="modal-action">
          <button type="button" class="btn btn-ghost" (click)="canceled.emit()">
            {{ cancelLabel() }}
          </button>
          <button type="button" [class]="confirmBtnClass()" (click)="confirm.emit()">
            {{ confirmLabel() }}
          </button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button type="button" (click)="canceled.emit()">close</button>
      </form>
    </div>
  `,
})
export class ConfirmModalComponent {
  readonly visible = input<boolean>(false);
  readonly title = input<string>('');
  readonly message = input<string | undefined>(undefined);
  readonly confirmLabel = input<string>('Confirm');
  readonly cancelLabel = input<string>('Cancel');
  readonly variant = input<ConfirmVariant>('primary');

  readonly confirm = output<void>();
  readonly canceled = output<void>();

  protected confirmBtnClass = computed(() => {
    const v = this.variant();
    return v === 'danger' ? 'btn btn-error' : 'btn btn-primary';
  });
}
