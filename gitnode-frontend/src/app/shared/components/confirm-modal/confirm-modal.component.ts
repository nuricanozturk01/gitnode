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

import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { ModalFocusDirective } from '../../directives/modal-focus.directive';

export type ConfirmVariant = 'danger' | 'primary';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-confirm-modal',
  standalone: true,
  imports: [ModalFocusDirective],
  template: `
    <div class="modal" [class.modal-open]="visible()">
      @if (visible()) {
        <div
          class="modal-box"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="confirm-modal-title"
          [attr.aria-describedby]="message() ? 'confirm-modal-message' : null"
          appModalFocus
          (appModalDismiss)="canceled.emit()"
        >
          <h3 id="confirm-modal-title" class="text-base-content text-lg font-bold">{{ title() }}</h3>
          @if (message()) {
            <p id="confirm-modal-message" class="text-base-content/80 py-4">{{ message() }}</p>
          }
          <div class="modal-action">
            <button type="button" class="btn btn-ghost" data-modal-initial (click)="canceled.emit()">
              {{ cancelLabel() }}
            </button>
            <button type="button" [class]="confirmBtnClass()" (click)="confirm.emit()">
              {{ confirmLabel() }}
            </button>
          </div>
        </div>
      }
      <form method="dialog" class="modal-backdrop">
        <button type="button" tabindex="-1" aria-hidden="true" (click)="canceled.emit()">close</button>
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
