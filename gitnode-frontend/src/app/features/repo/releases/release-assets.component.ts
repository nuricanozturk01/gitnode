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

import { ChangeDetectionStrategy, Component, inject, input, signal, type OnInit } from '@angular/core';
import { ReleaseService } from '../../../core/release/services/release.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { ReleaseAssetInfo } from '../../../domain/release/models/release-info.model';

/** Files attached to a release: list, download, and (for maintainers) upload by picker or drag and drop. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-release-assets',
  host: { class: 'block' },
  standalone: true,
  templateUrl: './release-assets.component.html',
})
export class ReleaseAssetsComponent implements OnInit {
  private readonly releaseService = inject(ReleaseService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly owner = input.required<string>();
  readonly repo = input.required<string>();
  readonly releaseId = input.required<string>();
  readonly canManage = input(false);

  readonly assets = signal<ReleaseAssetInfo[]>([]);
  readonly failed = signal(false);
  readonly uploading = signal(false);
  readonly progress = signal(0);
  readonly currentFile = signal('');
  readonly dragging = signal(false);
  readonly error = signal<string | null>(null);
  readonly downloading = signal<string | null>(null);
  private loadedFor = '';

  ngOnInit(): void {
    void this.load();
  }

  async load(): Promise<void> {
    const id = this.releaseId();
    this.loadedFor = id;
    this.failed.set(false);
    try {
      const list = await this.releaseService.listAssets(this.owner(), this.repo(), id);
      if (this.loadedFor === id) this.assets.set(list);
    } catch {
      if (this.loadedFor !== id) return;
      this.assets.set([]);
      this.failed.set(true);
    }
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
  }

  onFilesPicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    void this.upload(files);
  }

  onDragOver(event: DragEvent): void {
    if (!this.canManage() || this.uploading()) return;
    event.preventDefault();
    this.dragging.set(true);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    if (!this.canManage()) return;
    void this.upload(Array.from(event.dataTransfer?.files ?? []));
  }

  async upload(files: File[]): Promise<void> {
    if (!files.length || this.uploading() || !this.canManage()) return;
    this.uploading.set(true);
    this.error.set(null);
    let done = 0;
    try {
      for (const file of files) {
        this.currentFile.set(file.name);
        this.progress.set(0);
        const created = await this.releaseService.uploadAsset(
          this.owner(),
          this.repo(),
          this.releaseId(),
          file,
          (percent) => this.progress.set(percent),
        );
        done++;
        this.assets.update((list) => [...list, created].sort((a, b) => a.name.localeCompare(b.name)));
      }
      this.toast.success(done === 1 ? 'File attached' : `${done} files attached`);
    } catch (error) {
      const message = (error as { error?: { message?: string } })?.error?.message;
      this.error.set(message ?? 'Could not upload the file. Check its size and name.');
    } finally {
      this.uploading.set(false);
      this.currentFile.set('');
    }
  }

  async download(asset: ReleaseAssetInfo): Promise<void> {
    if (this.downloading()) return;
    this.downloading.set(asset.id);
    try {
      const blob = await this.releaseService.downloadAsset(this.owner(), this.repo(), this.releaseId(), asset.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = asset.name;
      a.rel = 'noopener';
      a.click();
      URL.revokeObjectURL(url);
      this.assets.update((list) => list.map((x) => (x.id === asset.id ? { ...x, downloadCount: x.downloadCount + 1 } : x)));
    } catch {
      this.toast.error('Could not download the file');
    } finally {
      this.downloading.set(null);
    }
  }

  async remove(asset: ReleaseAssetInfo): Promise<void> {
    if (!(await this.confirmModal.confirm('Delete file', `Delete ${asset.name} from this release?`, { confirmLabel: 'Delete', variant: 'danger' }))) return;
    try {
      await this.releaseService.deleteAsset(this.owner(), this.repo(), this.releaseId(), asset.id);
      this.assets.update((list) => list.filter((x) => x.id !== asset.id));
    } catch {
      this.toast.error('Could not delete the file');
    }
  }
}
