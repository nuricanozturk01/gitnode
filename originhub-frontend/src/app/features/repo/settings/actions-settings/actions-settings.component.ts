import { ChangeDetectionStrategy, Component, inject, input, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { SecretService } from '../../../../core/actions/services/secret.service';
import { ConfirmModalService } from '../../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../../core/toast/toast.service';
import type { SecretInfo } from '../../../../domain/actions/models/secret.model';
import { copyTextToClipboard } from '../../../../shared/utils/clipboard.util';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-actions-settings',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './actions-settings.component.html',
  styleUrl: './actions-settings.component.css',
})
export class ActionsSettingsComponent implements OnInit {
  readonly owner = input.required<string>();
  readonly repoName = input.required<string>();
  readonly canWrite = input.required<boolean>();

  private readonly secretService = inject(SecretService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly secrets = signal<SecretInfo[]>([]);
  readonly loadingSecrets = signal(false);
  readonly newSecretName = signal('');
  readonly newSecretValue = signal('');
  readonly savingSecret = signal(false);
  readonly showAddSecret = signal(false);
  readonly editingSecretName = signal<string | null>(null);

  ngOnInit(): void {
    void this.loadSecrets();
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

  get isSecretFormOpen(): boolean {
    return this.showAddSecret() || this.editingSecretName() !== null;
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

  async copyExpr(name: string): Promise<void> {
    await copyTextToClipboard(`\${{ secrets.${name} }}`);
    this.toast.success('Copied to clipboard');
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
