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

import { Component, inject, signal, computed, effect } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { AuthService } from '../../core/auth/services/auth.service';
import { TokenService } from '../../core/auth/services/token.service';
import { ThemeService } from '../../core/theme/theme.service';
import { UserService } from '../../core/user/services/user.service';
import type { User } from '../../domain/auth/models/user.model';

@Component({
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
  readonly theme = inject(ThemeService);

  readonly isLoggedIn = this.tokenService.isLoggedIn;

  readonly user = signal<User | null>(null);

  readonly avatarUrl = computed(() => this.user()?.avatarUrl ?? '');
  readonly userEmail = computed(() => this.user()?.email ?? '');

  constructor() {
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
          isAdmin: false,
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
