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

import {
  Component,
  ChangeDetectionStrategy,
  DestroyRef,
  afterNextRender,
  inject,
  signal,
  computed,
  effect,
} from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { AuthService } from '../../core/auth/services/auth.service';
import { TokenService } from '../../core/auth/services/token.service';
import { ThemeService } from '../../core/theme/theme.service';
import { UserService } from '../../core/user/services/user.service';
import type { User } from '../../domain/auth/models/user.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-navbar',
  standalone: true,
  imports: [LucideAngularModule, RouterLink, RouterLinkActive, AvatarComponent],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css',
})
export class NavbarComponent {
  private readonly authService = inject(AuthService);
  private readonly tokenService = inject(TokenService);
  private readonly userService = inject(UserService);
  private readonly destroyRef = inject(DestroyRef);
  readonly theme = inject(ThemeService);

  /** 0 at page top, 1 after ~72px scroll — drives glass + compact sizing. */
  readonly scrollProgress = signal(0);
  readonly isCompact = computed(() => this.scrollProgress() > 0.08);

  readonly isLoggedIn = this.tokenService.isLoggedIn;

  readonly user = signal<User | null>(null);

  readonly avatarUrl = computed(() => this.user()?.avatarUrl ?? '');
  readonly userEmail = computed(() => this.user()?.email ?? '');

  constructor() {
    afterNextRender(() => {
      const updateScroll = (): void => {
        const progress = Math.min(1, Math.max(0, window.scrollY / 72));
        this.scrollProgress.set(progress);
      };

      updateScroll();
      window.addEventListener('scroll', updateScroll, { passive: true });
      this.destroyRef.onDestroy(() => window.removeEventListener('scroll', updateScroll));
    });

    effect(() => {
      if (this.tokenService.isLoggedIn()) {
        this.loadUser();
      } else {
        this.user.set(null);
      }
    });
  }

  private async loadUser(): Promise<void> {
    try {
      const u = await this.userService.getMe();
      this.user.set(u);
    } catch {
      const username = this.tokenService.getUsername();
      if (username) {
        this.user.set({
          id: '',
          username,
          email: '',
          displayName: username,
          avatarUrl: null,
          bio: null,
          website: null,
          location: null,
          profileReadme: null,
          createdAt: '',
          updatedAt: '',
        });
      }
    }
  }

  async logout(): Promise<void> {
    await this.authService.logout();
  }
}
