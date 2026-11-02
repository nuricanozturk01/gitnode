import { Component, ChangeDetectionStrategy, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../core/auth/services/auth.service';
import { TokenService } from '../../core/auth/services/token.service';
import { ThemeService } from '../../core/theme/theme.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule],
  templateUrl: './admin-layout.component.html',
  host: { '(document:keydown.escape)': 'drawerOpen.set(false)' },
})
export class AdminLayoutComponent {
  readonly authService = inject(AuthService);
  readonly tokenService = inject(TokenService);
  readonly theme = inject(ThemeService);
  readonly drawerOpen = signal(false);

  constructor() {
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => this.drawerOpen.set(false));
  }

  logout(): void {
    void this.authService.logout();
  }
}
