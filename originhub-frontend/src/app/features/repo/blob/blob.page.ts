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
  effect,
  inject,
  signal,
  computed,
  ViewEncapsulation,
  OnDestroy,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { grandParentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { FileSizePipe } from '../../../shared/pipes/file-size.pipe';
import { environment } from '../../../../environments/environment';
import type { BlobResponse } from '../../../domain/repository/models/blob-response.model';
import type { BreadcrumbItem } from '../shared/repo-breadcrumb.component';
import { RepoBreadcrumbComponent } from '../shared/repo-breadcrumb.component';
import { ThemeService } from '../../../core/theme/theme.service';
import { DomSanitizer, SafeHtml, SafeResourceUrl, SafeUrl } from '@angular/platform-browser';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ToastService } from '../../../core/toast/toast.service';
import { decodeBase64Utf8 } from '../shared/utils/encoding';
import type { HLJSApi } from 'highlight.js';

const IMAGE_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'ico', 'bmp', 'avif']);

const TEXT_SIZE_LIMIT = 512 * 1024; // 512KB - avoid loading huge text into memory

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-blob',
  standalone: true,
  imports: [LucideAngularModule, FileSizePipe, RepoBreadcrumbComponent],
  encapsulation: ViewEncapsulation.None,
  templateUrl: './blob.page.html',
  styleUrl: './blob.page.css',
})
export class BlobPage implements OnDestroy {
  protected readonly environment = environment;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly appTheme = inject(ThemeService);
  readonly repoContext = inject(RepoContextService);
  private readonly toast = inject(ToastService);

  readonly blobSyntaxTheme = computed(() => (this.appTheme.isDark() ? 'dark' : 'light'));

  readonly blob = signal<BlobResponse | null>(null);
  readonly loading = signal(true);
  readonly copied = signal(false);
  readonly branch = signal('');
  readonly path = signal('');
  readonly highlightedLines = signal<SafeHtml[]>([]);
  readonly pdfObjectUrl = signal<SafeResourceUrl | null>(null);
  readonly imageObjectUrl = signal<SafeUrl | null>(null);
  readonly rawBlobUrl = signal<string | null>(null);

  readonly isEditing = signal(false);
  readonly editContent = signal('');
  readonly commitMessage = signal('');
  readonly commitDescription = signal('');
  readonly saving = signal(false);

  private objectUrlToRevoke: string | null = null;
  private copiedTimer: ReturnType<typeof setTimeout> | null = null;
  private hljsModule: Promise<HLJSApi> | null = null;
  private highlightGeneration = 0;

  private readonly repoRootParams = grandParentParamMapSignal(this.route);
  private readonly urlSegments = toSignal(this.route.url, { initialValue: this.route.snapshot.url });
  readonly owner = computed(() => this.repoRootParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRootParams().get('repo') ?? '');

  private readonly routeKey = computed(() => {
    const path = this.urlSegments()
      .map((s) => s.path)
      .join('/');
    return `${this.owner()}/${this.repoName()}/${path}`;
  });

  readonly fileName = computed(() => {
    const p = this.path();
    try {
      const decoded = decodeURIComponent(p);
      return decoded.split('/').pop() ?? decoded;
    } catch {
      return p.split('/').pop() ?? p;
    }
  });

  readonly breadcrumbItems = computed((): BreadcrumbItem[] => {
    const p = this.path();
    if (!p) return [];
    const parts = p.split('/').filter(Boolean);
    return parts.map((name, i) => {
      const pathSeg = parts.slice(0, i + 1).join('/');
      try {
        return { name: decodeURIComponent(name), path: pathSeg, isLast: i === parts.length - 1 };
      } catch {
        return { name, path: pathSeg, isLast: i === parts.length - 1 };
      }
    });
  });

  readonly decodedContent = computed(() => {
    const b = this.blob();
    if (!b?.content || b.isBinary) return '';
    return atob(b.content);
  });

  constructor() {
    effect(() => {
      this.routeKey();
      this.parseUrlAndLoad();
    });
  }

  private parseUrlAndLoad(): void {
    const url = this.router.url;
    const match = url.match(/\/blob\/(.+)$/);
    if (!match) {
      this.branch.set('main');
      this.path.set('');
      this.loading.set(false);
      return;
    }
    const parts = match[1].split('/').filter(Boolean);
    this.branch.set(decodeURIComponent(parts[0] ?? 'main'));
    this.path.set(
      parts
        .slice(1)
        .map((segment) => decodeURIComponent(segment))
        .join('/'),
    );
    this.loadBlob();
  }

  private async loadBlob(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const branch = this.branch();
    const path = this.path();
    if (!owner || !repo || !path) {
      this.loading.set(false);
      return;
    }
    this.highlightGeneration++;
    this.revokeBlobUrl();
    this.pdfObjectUrl.set(null);
    this.imageObjectUrl.set(null);
    this.rawBlobUrl.set(null);
    this.isEditing.set(false);
    this.loading.set(true);

    const ext = path.toLowerCase().split('.').pop() ?? '';
    if (ext === 'pdf') {
      await this.loadPdfAsBlob(owner, repo, branch, path);
      return;
    }
    if (IMAGE_EXTENSIONS.has(ext)) {
      await this.loadImageAsBlob(owner, repo, branch, path);
      return;
    }

    const url = `${environment.apiUrl}/api/repos/${owner}/${repo}/blob/${branch}/${path}`;
    try {
      const data = await firstValueFrom(this.http.get<BlobResponse>(url));
      this.blob.set(data);
      this.loading.set(false);
      if (!data.isBinary && data.size <= TEXT_SIZE_LIMIT) {
        this.processHighlighting(data);
      } else if (!data.isBinary && data.size > TEXT_SIZE_LIMIT) {
        this.highlightedLines.set([]);
        this.rawBlobUrl.set(this.buildRawUrl(owner, repo, branch, path));
      }
    } catch {
      this.loading.set(false);
    }
  }

  private buildRawUrl(owner: string, repo: string, branch: string, path: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/raw/${branch}/${path}`;
  }

  private async loadPdfAsBlob(owner: string, repo: string, branch: string, path: string): Promise<void> {
    const rawUrl = this.buildRawUrl(owner, repo, branch, path);
    try {
      const blob = await firstValueFrom(this.http.get(rawUrl, { responseType: 'blob' }));
      const objectUrl = URL.createObjectURL(blob);
      this.objectUrlToRevoke = objectUrl;
      this.pdfObjectUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl));
      this.blob.set({
        path,
        name: path.split('/').pop() ?? path,
        sha: '',
        size: blob.size,
        content: '',
        isBinary: true,
        language: null,
        lineCount: 0,
      });
      this.loading.set(false);
    } catch {
      this.loading.set(false);
    }
  }

  private async loadImageAsBlob(owner: string, repo: string, branch: string, path: string): Promise<void> {
    const rawUrl = this.buildRawUrl(owner, repo, branch, path);
    try {
      const blob = await firstValueFrom(this.http.get(rawUrl, { responseType: 'blob' }));
      const objectUrl = URL.createObjectURL(blob);
      this.objectUrlToRevoke = objectUrl;
      this.imageObjectUrl.set(this.sanitizer.bypassSecurityTrustUrl(objectUrl));
      this.blob.set({
        path,
        name: path.split('/').pop() ?? path,
        sha: '',
        size: blob.size,
        content: '',
        isBinary: true,
        language: null,
        lineCount: 0,
      });
      this.loading.set(false);
    } catch {
      this.loading.set(false);
    }
  }

  enterEditMode(): void {
    const b = this.blob();
    if (!b || b.isBinary) return;
    this.editContent.set(decodeBase64Utf8(b.content));
    this.commitMessage.set(`Update ${this.fileName()}`);
    this.commitDescription.set('');
    this.isEditing.set(true);
  }

  cancelEdit(): void {
    this.isEditing.set(false);
    this.editContent.set('');
    this.commitMessage.set('');
    this.commitDescription.set('');
  }

  async commitFile(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const branch = this.branch();
    const path = this.path();
    const msg = this.commitMessage().trim();
    if (!owner || !repo || !branch || !path || !msg) return;

    this.saving.set(true);
    try {
      const url = `${environment.apiUrl}/api/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/blob/${encodeURIComponent(branch)}/${path}`;
      const result = await firstValueFrom(
        this.http.put<BlobResponse>(url, {
          content: this.editContent(),
          commitMessage: msg,
          commitDescription: this.commitDescription().trim() || null,
        }),
      );
      this.blob.set(result);
      this.isEditing.set(false);
      this.editContent.set('');
      this.processHighlighting(result);
      this.toast.success('Changes committed');
    } catch {
      this.toast.error('Could not commit changes');
    } finally {
      this.saving.set(false);
    }
  }

  private revokeBlobUrl(): void {
    if (this.objectUrlToRevoke) {
      URL.revokeObjectURL(this.objectUrlToRevoke);
      this.objectUrlToRevoke = null;
    }
  }

  ngOnDestroy(): void {
    this.revokeBlobUrl();
    if (this.copiedTimer !== null) {
      clearTimeout(this.copiedTimer);
      this.copiedTimer = null;
    }
  }

  private processHighlighting(b: BlobResponse): void {
    if (b.isBinary) {
      this.highlightedLines.set([]);
      return;
    }
    const generation = ++this.highlightGeneration;
    void this.applySyntaxHighlight(b, generation);
  }

  private loadHljs(): Promise<HLJSApi> {
    if (!this.hljsModule) {
      this.hljsModule = import('highlight.js').then((m) => m.default);
    }
    return this.hljsModule;
  }

  private async applySyntaxHighlight(b: BlobResponse, generation: number): Promise<void> {
    const raw = atob(b.content);
    let highlighted: string;

    if (b.language && b.language !== 'plaintext') {
      const hljs = await this.loadHljs();
      if (generation !== this.highlightGeneration) return;
      if (hljs.getLanguage(b.language)) {
        highlighted = hljs.highlight(raw, { language: b.language, ignoreIllegals: true }).value;
      } else {
        highlighted = escapeHtml(raw);
      }
    } else {
      highlighted = escapeHtml(raw);
    }

    if (generation !== this.highlightGeneration) return;

    const lines = highlighted.split('\n').map((line) => this.sanitizer.bypassSecurityTrustHtml(line || '&nbsp;'));
    this.highlightedLines.set(lines);
  }

  copyContent(): void {
    navigator.clipboard.writeText(this.decodedContent());
    this.scheduleCopied();
  }

  copyRawUrl(): void {
    const url = `${environment.apiUrl}/api/repos/${this.owner()}/${this.repoName()}/raw/${this.branch()}/${this.path()}`;
    navigator.clipboard.writeText(url);
    this.scheduleCopied();
  }

  private scheduleCopied(): void {
    this.copied.set(true);
    if (this.copiedTimer !== null) clearTimeout(this.copiedTimer);
    this.copiedTimer = setTimeout(() => {
      this.copied.set(false);
      this.copiedTimer = null;
    }, 2000);
  }
}

function escapeHtml(raw: string): string {
  return raw.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
