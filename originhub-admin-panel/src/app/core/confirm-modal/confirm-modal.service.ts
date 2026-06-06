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
