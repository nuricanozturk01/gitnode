import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';

export type ConfirmVariant = 'danger' | 'primary';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
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
