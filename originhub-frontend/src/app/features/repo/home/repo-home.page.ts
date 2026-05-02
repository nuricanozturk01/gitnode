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

import { Component, inject, signal, computed, SecurityContext, OnDestroy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { finalize, firstValueFrom } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { FileSizePipe } from '../../../shared/pipes/file-size.pipe';
import { BranchService } from '../../../core/branch/services/branch.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { environment } from '../../../../environments/environment';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';
import type { TreeResponse, TreeResponseEntry } from '../../../domain/repository/models/tree-response.model';
import type { BranchInfo } from '../../../domain/repository/models/branch-info.model';
import type { BlobResponse } from '../../../domain/repository/models/blob-response.model';
import { decodeBase64Utf8 } from '../shared/utils/encoding';
import { ToastService } from '../../../core/toast/toast.service';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';

@Component({
  selector: 'app-repo-home',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, FileSizePipe],
  templateUrl: './repo-home.page.html',
  styleUrl: './repo-home.page.css',
})
export class RepoHomePage implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly branchService = inject(BranchService);
  private readonly repoContext = inject(RepoContextService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly toast = inject(ToastService);

  readonly tree = signal<TreeResponseEntry[]>([]);
  readonly branches = signal<BranchInfo[]>([]);
  readonly loading = signal(true);
  readonly isEmpty = signal(false);
  readonly selectedBranch = signal('');
  readonly readmeHtml = signal<SafeHtml | null>(null);

  readonly branch = computed(() => {
    const sel = this.selectedBranch();
    if (sel) return sel;
    const def = this.repoContext.defaultBranch();
    const branches = this.branches();
    const defaultBranch = branches.find((b) => b.isDefault);
    return defaultBranch?.name ?? def ?? 'main';
  });

  readonly cloneDialogOpen = signal(false);
  readonly copied = signal<'https' | 'ssh' | null>(null);
  readonly zipDownloading = signal(false);
  private copiedTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');
  /** Smart HTTP Git under `/git/{owner}/{repo}` (no `.git` suffix; server maps to bare repo on disk). */
  readonly httpsCloneUrl = computed(() => {
    const base = environment.apiUrl.replace(/\/$/, '');
    const o = encodeURIComponent(this.owner());
    const r = encodeURIComponent(this.repoName());
    return `${base}/git/${o}/${r}`;
  });

  readonly sshCloneUrl = computed(
    () => `ssh://${environment.gitUrl}/${this.owner()}/${this.repoName()}.git`,
  );

  constructor() {
    const parent = this.route.parent;
    if (!parent) {
      this.loading.set(false);
      return;
    }
    parent.paramMap.pipe(takeUntilDestroyed()).subscribe(() => {
      this.selectedBranch.set('');
      void this.loadData();
    });
  }

  ngOnDestroy(): void {
    if (this.copiedTimer !== null) {
      clearTimeout(this.copiedTimer);
      this.copiedTimer = null;
    }
  }

  private async loadData(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.isEmpty.set(false);
    try {
      const branchesData = await this.branchService.getAll(owner, repo);
      this.branches.set(branchesData);

      if (branchesData.length === 0) {
        this.isEmpty.set(true);
        return;
      }

      const defaultBranch = branchesData.find((b) => b.isDefault);
      const branchForTree = this.selectedBranch() || (defaultBranch?.name ?? this.repoContext.defaultBranch());
      if (!this.selectedBranch()) {
        this.selectedBranch.set(branchForTree);
      }
      await this.loadTreeOnce(owner, repo, branchForTree);
      await this.loadReadme(owner, repo, branchForTree);
    } catch {
      this.branches.set([]);
      this.isEmpty.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  private loadTreeOnce(owner: string, repo: string, branch: string): Promise<void> {
    return firstValueFrom(
      this.http.get<TreeResponse>(`${environment.apiUrl}/api/repos/${owner}/${repo}/tree/${branch}`),
    )
      .then((data) => {
        this.tree.set(data.entries ?? []);
      })
      .catch(() => {
        this.tree.set([]);
      });
  }

  private async loadReadme(owner: string, repo: string, branch: string): Promise<void> {
    const candidates = ['README.md'];
    for (const candidate of candidates) {
      const found = await this.tryFetchReadme(owner, repo, branch, candidate);
      if (found) return;
    }
    this.readmeHtml.set(null);
  }

  private async tryFetchReadme(owner: string, repo: string, branch: string, filename: string): Promise<boolean> {
    try {
      const data = await firstValueFrom(
        this.http.get<BlobResponse>(`${environment.apiUrl}/api/repos/${owner}/${repo}/blob/${branch}/${filename}`),
      );
      if (data.isBinary || !data.content) {
        return false;
      }
      const raw = decodeBase64Utf8(data.content);
      const html = await marked.parse(raw);
      const sanitized = this.sanitizer.sanitize(SecurityContext.HTML, html) ?? '';
      this.readmeHtml.set(this.sanitizer.bypassSecurityTrustHtml(sanitized));
      return true;
    } catch {
      return false;
    }
  }

  onBranchChange(branchName: string): void {
    this.selectedBranch.set(branchName);
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    this.loadTreeOnce(owner, repo, branchName);
    this.loadReadme(owner, repo, branchName);
  }

  entryRoute(entry: TreeResponseEntry): string[] {
    const pathSegments = entry.path ? entry.path.split('/').filter(Boolean) : [];
    if (entry.type === 'TREE') {
      return ['/', this.owner(), this.repoName(), 'tree', this.branch(), ...pathSegments];
    }
    return ['/', this.owner(), this.repoName(), 'blob', this.branch(), ...pathSegments];
  }

  isTree(entry: TreeResponseEntry): boolean {
    return entry.type === 'TREE';
  }

  copyText(value: string, type: 'https' | 'ssh'): void {
    navigator.clipboard.writeText(value);
    this.toast.success('Copied to clipboard');
    this.scheduleCopied(type);
  }

  private scheduleCopied(type: 'https' | 'ssh'): void {
    this.copied.set(type);
    if (this.copiedTimer !== null) clearTimeout(this.copiedTimer);
    this.copiedTimer = setTimeout(() => {
      this.copied.set(null);
      this.copiedTimer = null;
    }, 2000);
  }

  openCloneDialog(): void {
    this.cloneDialogOpen.set(true);
  }

  closeCloneDialog(): void {
    this.cloneDialogOpen.set(false);
  }

  downloadBranchZip(): void {
    const owner = this.owner();
    const repo = this.repoName();
    const branch = this.branch();
    if (!owner || !repo || !branch) return;

    const url = `${environment.apiUrl}/api/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/archive/${encodeURIComponent(branch)}`;

    this.zipDownloading.set(true);
    this.http
      .get(url, { responseType: 'blob', observe: 'response' })
      .pipe(finalize(() => this.zipDownloading.set(false)))
      .subscribe({
        next: (resp) => {
          const blob = resp.body;
          if (!blob) {
            this.toast.error('Could not download ZIP');
            return;
          }
          const fromHeader = this.fileNameFromContentDisposition(resp.headers.get('Content-Disposition'));
          const fileName = fromHeader ?? this.fallbackZipFileName(owner, repo, branch);
          const objectUrl = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = objectUrl;
          a.download = fileName;
          a.rel = 'noopener';
          a.click();
          URL.revokeObjectURL(objectUrl);
        },
        error: () => this.toast.error('Could not download ZIP'),
      });
  }

  private fallbackZipFileName(owner: string, repo: string, branch: string): string {
    const safe = (s: string) =>
      s
        .replace(/[\u0000-\u001f/\\:*?"<>|]/g, '-')
        .replace(/--+/g, '-')
        .trim() || 'archive';
    return `${safe(owner)}-${safe(repo)}-${safe(branch)}.zip`;
  }

  private fileNameFromContentDisposition(header: string | null): string | null {
    if (!header) return null;
    const utf8 = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(header);
    if (utf8?.[1]) {
      try {
        return decodeURIComponent(utf8[1].trim().replace(/^"(.*)"$/, '$1'));
      } catch {
        return utf8[1].trim().replace(/^"(.*)"$/, '$1');
      }
    }
    const ascii = /filename="([^"]+)"/i.exec(header);
    if (ascii?.[1]) return ascii[1];
    const plain = /filename=([^;]+)/i.exec(header);
    return plain?.[1]?.trim().replace(/^"(.*)"$/, '$1') ?? null;
  }
}
