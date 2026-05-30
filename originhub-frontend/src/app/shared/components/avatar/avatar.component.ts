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

import { Component, input, computed } from '@angular/core';
import { gravatarUrl } from '../../../core/gravatar/gravatar.util';

type AvatarSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl';

@Component({
  selector: 'app-avatar',
  standalone: true,
  template: `
    <div class="avatar shrink-0">
      @if (effectiveAvatarUrl()) {
        <div class="bg-base-300 overflow-hidden rounded-full" [class]="sizeToDim()">
          <img [src]="effectiveAvatarUrl()!" [alt]="alt()" class="h-full w-full object-cover" />
        </div>
      } @else {
        <div
          class="text-primary-content bg-primary flex items-center justify-center rounded-full font-semibold"
          [class]="sizeToDim()"
        >
          <span [class]="sizeToText()">{{ fallback() }}</span>
        </div>
      }
    </div>
  `,
})
export class AvatarComponent {
  readonly avatarUrl = input<string | null | undefined>(null);
  readonly email = input<string | null | undefined>(null);
  readonly username = input<string | null | undefined>(null);
  readonly fallback = input<string>('?');
  readonly size = input<AvatarSize>('md');
  readonly alt = input<string>('');

  protected effectiveAvatarUrl = computed(() =>
    gravatarUrl(this.avatarUrl(), this.email(), this.username(), this.sizeToPixels()),
  );

  protected sizeToDim(): string {
    const map: Record<AvatarSize, string> = {
      xs: 'w-6 h-6',
      sm: 'w-8 h-8',
      md: 'w-10 h-10',
      lg: 'w-12 h-12',
      xl: 'w-16 h-16',
      '2xl': 'w-32 h-32',
    };
    return map[this.size()];
  }

  protected sizeToPixels(): number {
    const map: Record<AvatarSize, number> = {
      xs: 48,
      sm: 64,
      md: 80,
      lg: 96,
      xl: 128,
      '2xl': 256,
    };
    return map[this.size()];
  }

  protected sizeToText(): string {
    const map: Record<AvatarSize, string> = {
      xs: 'text-xs',
      sm: 'text-sm',
      md: 'text-base',
      lg: 'text-lg',
      xl: 'text-xl',
      '2xl': 'text-4xl',
    };
    return map[this.size()];
  }
}
