import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Location } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../../shared/pipes/relative-time.pipe';
import { RunnerService } from '../../../../core/actions/services/runner.service';
import { SecretService } from '../../../../core/actions/services/secret.service';
import { ConfirmModalService } from '../../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../../core/toast/toast.service';
import { environment } from '../../../../../environments/environment';
import type { RunnerInfo, RegistrationToken } from '../../../../domain/actions/models/runner.model';
import type { SecretInfo } from '../../../../domain/actions/models/secret.model';
import { copyTextToClipboard } from '../../../../shared/utils/clipboard.util';
import { normalizeStatusKey, runnerStatusLabel } from '../../../../shared/utils/workflow-status.utils';
import { buildCompoundFragment, parseCompoundSubTab, replaceUrlFragment } from '../../../../shared/utils/url-tab.utils';

const ACTIONS_SETTINGS_PARENT_TAB = 'actions';
const ACTIONS_SUB_TABS = ['runners', 'secrets'] as const;
type ActionsSubTab = (typeof ACTIONS_SUB_TABS)[number];
const DEFAULT_ACTIONS_SUB_TAB: ActionsSubTab = 'runners';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-actions-settings',
  standalone: true,
  imports: [FormsModule, LucideAngularModule, RelativeTimePipe],
  templateUrl: './actions-settings.component.html',
  styleUrl: './actions-settings.component.css',
})
export class ActionsSettingsComponent {
  readonly owner = input.required<string>();
  readonly repoName = input.required<string>();
  readonly canWrite = input.required<boolean>();

  private readonly runnerService = inject(RunnerService);
  private readonly secretService = inject(SecretService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);

  readonly subTab = signal<ActionsSubTab>(DEFAULT_ACTIONS_SUB_TAB);
  readonly runners = signal<RunnerInfo[]>([]);
  readonly secrets = signal<SecretInfo[]>([]);
  readonly loadingRunners = signal(false);
  readonly loadingSecrets = signal(false);
  readonly generatingToken = signal(false);
  readonly registrationToken = signal<RegistrationToken | null>(null);

  readonly newSecretName = signal('');
  readonly newSecretValue = signal('');
  readonly savingSecret = signal(false);
  readonly showAddSecret = signal(false);
  readonly editingSecretName = signal<string | null>(null);

  readonly isSecretFormOpen = computed(() => this.showAddSecret() || this.editingSecretName() !== null);

  readonly serverUrl = computed(() => environment.apiUrl);

  statusLabel = runnerStatusLabel;

  isRunnerOnline(status: string): boolean {
    return normalizeStatusKey(status) === 'online';
  }

  isRunnerBusy(status: string): boolean {
    return normalizeStatusKey(status) === 'busy';
  }

  isRunnerOffline(status: string): boolean {
    return normalizeStatusKey(status) === 'offline';
  }

  constructor() {
    this.route.fragment.pipe(takeUntilDestroyed()).subscribe((fragment) => {
      this.subTab.set(
        parseCompoundSubTab(fragment, ACTIONS_SETTINGS_PARENT_TAB, ACTIONS_SUB_TABS, DEFAULT_ACTIONS_SUB_TAB),
      );
    });

    effect(() => {
      if (this.owner() && this.repoName()) {
        this.load();
      }
    });
  }

  load(): void {
    void this.loadRunners();
    void this.loadSecrets();
  }

  setSubTab(tab: ActionsSubTab): void {
    this.subTab.set(tab);
    replaceUrlFragment(this.location, buildCompoundFragment(ACTIONS_SETTINGS_PARENT_TAB, tab, DEFAULT_ACTIONS_SUB_TAB));
  }

  async loadRunners(): Promise<void> {
    this.loadingRunners.set(true);
    try {
      this.runners.set(await this.runnerService.list(this.owner(), this.repoName()));
    } catch {
      this.runners.set([]);
    } finally {
      this.loadingRunners.set(false);
    }
  }

  async loadSecrets(): Promise<void> {
    this.loadingSecrets.set(true);
    try {
      this.secrets.set(await this.secretService.list(this.owner(), this.repoName()));
    } catch {
      this.secrets.set([]);
    } finally {
      this.loadingSecrets.set(false);
    }
  }

  async generateToken(): Promise<void> {
    if (!this.canWrite()) return;
    this.generatingToken.set(true);
    try {
      const token = await this.runnerService.createRegistrationToken(this.owner(), this.repoName());
      this.registrationToken.set(token);
      this.toast.success('Registration token generated');
    } catch {
      this.toast.error('Could not generate registration token');
    } finally {
      this.generatingToken.set(false);
    }
  }

  async copyToken(): Promise<void> {
    const token = this.registrationToken()?.token;
    if (!token) return;
    try {
      await copyTextToClipboard(token);
      this.toast.success('Token copied');
    } catch {
      this.toast.error('Could not copy token');
    }
  }

  async deleteRunner(runner: RunnerInfo): Promise<void> {
    if (!this.canWrite()) return;
    const confirmed = await this.confirmModal.confirm(
      'Remove runner',
      `Remove runner "${runner.name}"? It will stop receiving jobs.`,
      { variant: 'danger' },
    );
    if (!confirmed) return;
    try {
      await this.runnerService.delete(this.owner(), this.repoName(), runner.id);
      this.runners.update((list) => list.filter((r) => r.id !== runner.id));
      this.toast.success('Runner removed');
    } catch {
      this.toast.error('Could not remove runner');
    }
  }

  async saveSecret(): Promise<void> {
    if (!this.canWrite()) return;
    const name = this.newSecretName().trim();
    const value = this.newSecretValue();
    if (!name || !value) return;

    this.savingSecret.set(true);
    try {
      await this.secretService.createOrUpdate(this.owner(), this.repoName(), name, value);
      this.cancelSecretForm();
      await this.loadSecrets();
      this.toast.success('Secret saved');
    } catch {
      this.toast.error('Could not save secret');
    } finally {
      this.savingSecret.set(false);
    }
  }

  startAddSecret(): void {
    this.editingSecretName.set(null);
    this.newSecretName.set('');
    this.newSecretValue.set('');
    this.showAddSecret.set(true);
  }

  startEditSecret(name: string): void {
    this.showAddSecret.set(false);
    this.editingSecretName.set(name);
    this.newSecretName.set(name);
    this.newSecretValue.set('');
  }

  cancelSecretForm(): void {
    this.showAddSecret.set(false);
    this.editingSecretName.set(null);
    this.newSecretName.set('');
    this.newSecretValue.set('');
  }

  async deleteSecret(secret: SecretInfo): Promise<void> {
    if (!this.canWrite()) return;
    const confirmed = await this.confirmModal.confirm(
      'Delete secret',
      `Delete secret "${secret.name}"? Workflows using it will fail.`,
      { variant: 'danger' },
    );
    if (!confirmed) return;
    try {
      await this.secretService.delete(this.owner(), this.repoName(), secret.name);
      this.secrets.update((list) => list.filter((s) => s.name !== secret.name));
      this.toast.success('Secret deleted');
    } catch {
      this.toast.error('Could not delete secret');
    }
  }
}
