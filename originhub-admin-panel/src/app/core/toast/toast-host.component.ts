import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { ToastComponent } from '../../shared/components/toast/toast.component';
import { ToastService } from './toast.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-toast-host',
  standalone: true,
  imports: [ToastComponent],
  template: `
    <div class="toast toast-top toast-end z-[200] gap-2 p-4">
      @for (t of toastService.toasts(); track t.id) {
        <app-toast [message]="t.message" [type]="t.type" (dismiss)="toastService.dismiss(t.id)" />
      }
    </div>
  `,
})
export class ToastHostComponent {
  readonly toastService = inject(ToastService);
}
