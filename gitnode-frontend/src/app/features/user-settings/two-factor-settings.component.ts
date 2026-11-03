import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { firstValueFrom } from 'rxjs';
import QRCode from 'qrcode';
import { environment } from '../../../environments/environment';
import { ToastService } from '../../core/toast/toast.service';
import { profileErrorMessage } from '../../shared/utils/api-error.utils';

interface TwoFactorStatus {
  enabled: boolean;
  recoveryCodesLeft: number;
}
interface TwoFactorSetup {
  secret: string;
  otpAuthUri: string;
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-two-factor-settings',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  template: `
    <section class="repo-panel settings-section p-5 sm:p-6" aria-labelledby="two-factor-heading">
      <h2 id="two-factor-heading" class="text-base-content flex items-center gap-2 text-lg font-semibold">
        <lucide-icon name="shield" class="text-primary size-5 shrink-0"></lucide-icon>
        Two-factor authentication
      </h2>
      <p class="text-base-content/65 mt-1 text-sm">
        Require a code from an authenticator app (TOTP) when signing in with your password. When enabled, HTTPS Git with
        a password is blocked — use SSH keys.
      </p>
      @if (loading()) {
        <p class="mt-3 text-sm">Loading…</p>
      } @else if (recoveryCodes().length) {
        <div class="mt-4 space-y-2" role="status">
          <p class="text-sm font-medium">
            Two-factor authentication is on. Save these recovery codes now; each works once and they won't be shown
            again.
          </p>
          <pre class="bg-base-200 rounded p-3 font-mono text-sm">{{ recoveryCodes().join('\\n') }}</pre>
          <button type="button" class="btn btn-sm btn-outline" (click)="recoveryCodes.set([])">I saved them</button>
        </div>
      } @else if (status()?.enabled) {
        <p class="mt-3 text-sm">Enabled · {{ status()!.recoveryCodesLeft }} recovery codes left.</p>
        <form class="mt-3 flex max-w-sm flex-col gap-2" (ngSubmit)="disable()">
          <label for="tf-disable-password" class="settings-label">Password</label>
          <input
            id="tf-disable-password"
            name="password"
            type="password"
            class="input input-bordered input-sm"
            autocomplete="current-password"
            [(ngModel)]="password"
          />
          <label for="tf-disable-code" class="settings-label">Authentication or recovery code</label>
          <input
            id="tf-disable-code"
            name="code"
            type="text"
            class="input input-bordered input-sm"
            autocomplete="one-time-code"
            [(ngModel)]="code"
          />
          @if (error()) {
            <p class="text-error text-sm" role="alert">{{ error() }}</p>
          }
          <button type="submit" class="btn btn-error btn-sm" [disabled]="busy() || !password || !code">
            Disable two-factor
          </button>
        </form>
      } @else if (setup()) {
        <div class="mt-3 max-w-md space-y-2">
          <p class="text-sm">
            Add this key to your authenticator app (Google Authenticator, 1Password, Authy…), then enter the 6-digit
            code.
          </p>
          @if (qrDataUrl()) {
            <img
              [src]="qrDataUrl()"
              alt="QR code for your authenticator app"
              width="192"
              height="192"
              class="rounded bg-white p-2"
            />
          }
          <p class="text-xs">Can't scan? Enter this key manually:</p>
          <code class="bg-base-200 block rounded p-2 font-mono text-sm break-all select-all">{{
            setup()!.secret
          }}</code>
          <a class="link text-xs break-all" [href]="setup()!.otpAuthUri">Open in authenticator app</a>
          <form class="flex flex-col gap-2" (ngSubmit)="enable()">
            <label for="tf-enable-code" class="settings-label">Authentication code</label>
            <input
              id="tf-enable-code"
              name="code"
              type="text"
              inputmode="numeric"
              maxlength="6"
              class="input input-bordered input-sm"
              autocomplete="one-time-code"
              [(ngModel)]="code"
            />
            @if (error()) {
              <p class="text-error text-sm" role="alert">{{ error() }}</p>
            }
            <div class="flex gap-2">
              <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy() || code.length !== 6">
                Enable
              </button>
              <button type="button" class="btn btn-ghost btn-sm" (click)="cancel()">Cancel</button>
            </div>
          </form>
        </div>
      } @else {
        <button type="button" class="btn btn-primary btn-sm mt-3" [disabled]="busy()" (click)="startSetup()">
          Set up two-factor
        </button>
      }
    </section>
  `,
})
export class TwoFactorSettingsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly base = `${environment.apiUrl}/api/users/me/2fa`;

  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly status = signal<TwoFactorStatus | null>(null);
  readonly setup = signal<TwoFactorSetup | null>(null);
  readonly recoveryCodes = signal<string[]>([]);
  readonly qrDataUrl = signal('');
  password = '';
  code = '';

  async ngOnInit(): Promise<void> {
    await this.refresh();
  }

  private async refresh(): Promise<void> {
    try {
      this.status.set(await firstValueFrom(this.http.get<TwoFactorStatus>(this.base)));
    } catch (e) {
      this.toast.error(profileErrorMessage(e, 'Could not load two-factor status'));
    } finally {
      this.loading.set(false);
    }
  }

  async startSetup(): Promise<void> {
    await this.run(async () => {
      const setup = await firstValueFrom(this.http.post<TwoFactorSetup>(`${this.base}/setup`, {}));
      this.qrDataUrl.set(await QRCode.toDataURL(setup.otpAuthUri, { width: 192, margin: 1 }));
      this.setup.set(setup);
      this.code = '';
    });
  }

  async enable(): Promise<void> {
    await this.run(async () => {
      const res = await firstValueFrom(
        this.http.post<{ recoveryCodes: string[] }>(`${this.base}/enable`, { code: this.code }),
      );
      this.recoveryCodes.set(res.recoveryCodes);
      this.setup.set(null);
      this.qrDataUrl.set('');
      this.code = '';
      await this.refresh();
      this.toast.success('Two-factor authentication enabled');
    });
  }

  async disable(): Promise<void> {
    await this.run(async () => {
      await firstValueFrom(this.http.post<void>(`${this.base}/disable`, { password: this.password, code: this.code }));
      this.password = '';
      this.code = '';
      await this.refresh();
      this.toast.success('Two-factor authentication disabled');
    });
  }

  cancel(): void {
    this.setup.set(null);
    this.qrDataUrl.set('');
    this.error.set(null);
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (e) {
      this.error.set(profileErrorMessage(e, 'Request failed'));
    } finally {
      this.busy.set(false);
    }
  }
}
