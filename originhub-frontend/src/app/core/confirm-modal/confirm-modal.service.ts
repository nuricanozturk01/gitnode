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

import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'danger' | 'primary';
}

@Injectable({ providedIn: 'root' })
export class ConfirmModalService {
  readonly visible = signal(false);
  readonly title = signal('');
  readonly message = signal<string | undefined>(undefined);
  readonly confirmLabel = signal('Confirm');
  readonly cancelLabel = signal('Cancel');
  readonly variant = signal<'danger' | 'primary'>('primary');

  private resolveFn: ((value: boolean) => void) | null = null;

  confirm(title: string, message?: string, options?: ConfirmOptions): Promise<boolean> {
    // Resolve any previously pending confirmation as cancelled to prevent Promise leaks
    if (this.resolveFn) {
      this.resolveFn(false);
      this.resolveFn = null;
    }

    this.title.set(title);
    this.message.set(message);
    this.confirmLabel.set(options?.confirmLabel ?? 'Confirm');
    this.cancelLabel.set(options?.cancelLabel ?? 'Cancel');
    this.variant.set(options?.variant ?? 'primary');
    this.visible.set(true);

    return new Promise<boolean>((resolve) => {
      this.resolveFn = resolve;
    });
  }

  onConfirm(): void {
    this.visible.set(false);
    if (this.resolveFn) {
      this.resolveFn(true);
      this.resolveFn = null;
    }
  }

  onCancel(): void {
    this.visible.set(false);
    if (this.resolveFn) {
      this.resolveFn(false);
      this.resolveFn = null;
    }
  }
}
