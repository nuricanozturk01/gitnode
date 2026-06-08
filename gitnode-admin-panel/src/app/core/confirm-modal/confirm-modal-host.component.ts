import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { ConfirmModalComponent } from '../../shared/components/confirm-modal/confirm-modal.component';
import { ConfirmModalService } from './confirm-modal.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
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
