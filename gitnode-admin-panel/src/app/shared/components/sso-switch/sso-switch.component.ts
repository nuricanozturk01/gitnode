import { Component, ChangeDetectionStrategy, effect, ElementRef, inject, input, output } from '@angular/core';

export type SsoSwitchSize = 'sm' | 'md' | 'lg';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-sso-switch',
  standalone: true,
  templateUrl: './sso-switch.component.html',
  styleUrl: './sso-switch.component.css',
})
export class SsoSwitchComponent {
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly checked = input(false);
  readonly disabled = input(false);
  readonly size = input<SsoSwitchSize>('md');
  readonly showLabels = input(true);
  readonly ariaLabel = input('Toggle SSO');

  readonly checkedChange = output<boolean>();

  constructor() {
    effect(() => {
      const value = this.checked();
      const input = this.host.nativeElement.querySelector('input');
      if (input instanceof HTMLInputElement) {
        input.checked = value;
      }
    });
  }

  onInputChange(event: Event): void {
    if (this.disabled()) return;
    const next = (event.target as HTMLInputElement).checked;
    this.checkedChange.emit(next);
  }
}
