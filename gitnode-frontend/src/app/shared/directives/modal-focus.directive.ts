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

import { AfterViewInit, Directive, ElementRef, OnDestroy, inject, output } from '@angular/core';

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Makes an element behave like a modal dialog for keyboard and screen-reader users: focus moves
 * inside when it appears (to `[data-modal-initial]`, else the first control), Tab and Shift+Tab stay
 * inside, Escape emits `appModalDismiss`, and focus returns to the previously focused element when
 * it goes away.
 */
@Directive({
  selector: '[appModalFocus]',
  standalone: true,
  host: { tabindex: '-1', '(keydown)': 'onKeydown($event)' },
})
export class ModalFocusDirective implements AfterViewInit, OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private previous: HTMLElement | null = null;

  readonly appModalDismiss = output<void>();

  ngAfterViewInit(): void {
    this.previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const root = this.host.nativeElement;
    (root.querySelector<HTMLElement>('[data-modal-initial]') ?? this.focusable()[0] ?? root).focus();
  }

  ngOnDestroy(): void {
    const previous = this.previous;
    if (previous?.isConnected) previous.focus();
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.stopPropagation();
      this.appModalDismiss.emit();
      return;
    }
    if (event.key !== 'Tab') return;
    const controls = this.focusable();
    if (controls.length === 0) {
      event.preventDefault();
      return;
    }
    const first = controls[0];
    const last = controls[controls.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && (active === first || active === this.host.nativeElement)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private focusable(): HTMLElement[] {
    return Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
      (el) => el.getClientRects().length > 0,
    );
  }
}
