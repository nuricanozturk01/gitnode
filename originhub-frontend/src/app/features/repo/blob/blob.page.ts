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

import { Component, inject, signal, computed, ViewEncapsulation, OnDestroy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { FileSizePipe } from '../../../shared/pipes/file-size.pipe';
import { environment } from '../../../../environments/environment';
import type { BlobResponse } from '../../../domain/repository/models/blob-response.model';
import type { BreadcrumbItem } from '../shared/repo-breadcrumb.component';
import { RepoBreadcrumbComponent } from '../shared/repo-breadcrumb.component';
import { DomSanitizer, SafeHtml, SafeResourceUrl } from '@angular/platform-browser';
import hljs from 'highlight.js';

const TEXT_SIZE_LIMIT = 512 * 1024; // 512KB - avoid loading huge text into memory

@Component({
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

  readonly blob = signal<BlobResponse | null>(null);
  readonly loading = signal(true);
  readonly copied = signal(false);
  readonly branch = signal('');
  readonly path = signal('');
  readonly highlightedLines = signal<SafeHtml[]>([]);
  readonly pdfObjectUrl = signal<SafeResourceUrl | null>(null);
  readonly rawBlobUrl = signal<string | null>(null);
  private objectUrlToRevoke: string | null = null;
  private copiedTimer: ReturnType<typeof setTimeout> | null = null;

  readonly owner = computed(() => this.route.snapshot.parent?.parent?.paramMap.get('owner') ?? '');
  readonly repoName = computed(() => this.route.snapshot.parent?.parent?.paramMap.get('repo') ?? '');

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
    this.route.params.pipe(takeUntilDestroyed()).subscribe(() => this.parseUrlAndLoad());
    this.route.url.pipe(takeUntilDestroyed()).subscribe(() => this.parseUrlAndLoad());
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
    this.branch.set(parts[0] ?? 'main');
    this.path.set(parts.slice(1).join('/'));
    this.loadBlob();
  }

  private async loadBlob(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const branch = this.branch();
    const path = this.path();
    if (!owner || !repo || !path) return;
    this.revokeBlobUrl();
    this.pdfObjectUrl.set(null);
    this.rawBlobUrl.set(null);
    this.loading.set(true);

    const ext = path.toLowerCase().split('.').pop() ?? '';
    if (ext === 'pdf') {
      await this.loadPdfAsBlob(owner, repo, branch, path);
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

    const raw = atob(b.content);
    let highlighted: string;

    if (b.language && b.language !== 'plaintext' && hljs.getLanguage(b.language)) {
      highlighted = hljs.highlight(raw, { language: b.language, ignoreIllegals: true }).value;
    } else {
      highlighted = raw.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

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
