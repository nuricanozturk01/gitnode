import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error';

export interface Toast {
  id: string;
  message: string;
  type: ToastType;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);

  private nextId = 0;
  private readonly DEFAULT_DURATION = 4000;
  private readonly timers = new Map<string, ReturnType<typeof setTimeout>>();

  success(message: string, duration = this.DEFAULT_DURATION): void {
    this.show(message, 'success', duration);
  }

  error(message: string, duration = this.DEFAULT_DURATION): void {
    this.show(message, 'error', duration);
  }

  private show(message: string, type: ToastType, duration: number): void {
    const id = `toast-${++this.nextId}`;
    const toast: Toast = { id, message, type };
    this.toasts.update((list) => [...list, toast]);
    if (duration > 0) {
      const timer = setTimeout(() => {
        this.timers.delete(id);
        this.dismiss(id);
      }, duration);
      this.timers.set(id, timer);
    }
  }

  dismiss(id: string): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
