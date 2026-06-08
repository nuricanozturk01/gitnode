import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmModalHostComponent } from './core/confirm-modal/confirm-modal-host.component';
import { ToastHostComponent } from './core/toast/toast-host.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-root',
  imports: [RouterOutlet, ConfirmModalHostComponent, ToastHostComponent],
  template: `
    <router-outlet />
    <app-confirm-modal-host />
    <app-toast-host />
  `,
})
export class App {}
