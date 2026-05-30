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

import { Component, input, output } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import type { ToastType } from '../../../core/toast/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    <div
      role="alert"
      [class]="alertClass()"
      class="flex w-80 min-w-0 items-start gap-3 rounded-xl border px-4 py-3 shadow-lg transition-all"
    >
      @if (type() === 'success') {
        <div class="bg-success/10 flex size-9 shrink-0 items-center justify-center rounded-lg">
          <lucide-icon name="checkCircle" class="text-success size-5"></lucide-icon>
        </div>
      } @else {
        <div class="bg-error/10 flex size-9 shrink-0 items-center justify-center rounded-lg">
          <lucide-icon name="xCircle" class="text-error size-5"></lucide-icon>
        </div>
      }
      <span class="min-w-0 flex-1 py-0.5 text-sm font-medium break-words">{{ message() }}</span>
      <button
        type="button"
        class="btn btn-ghost btn-xs btn-circle shrink-0 opacity-70 hover:opacity-100"
        (click)="dismiss.emit()"
        aria-label="Close"
      >
        <lucide-icon name="x" class="size-4"></lucide-icon>
      </button>
    </div>
  `,
})
export class ToastComponent {
  readonly message = input.required<string>();
  readonly type = input<ToastType>('success');

  readonly dismiss = output<void>();

  protected alertClass = () => {
    const t = this.type();
    return t === 'success' ? 'border-success/30 bg-base-100' : 'border-error/30 bg-base-100';
  };
}
