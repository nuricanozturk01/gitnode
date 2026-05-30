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

import { Component, HostListener, inject, signal, computed, SecurityContext, OnDestroy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { finalize, firstValueFrom, noop } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { FileSizePipe } from '../../../shared/pipes/file-size.pipe';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { BranchService } from '../../../core/branch/services/branch.service';
import { TagService } from '../../../core/tag/services/tag.service';
import { ReleaseService } from '../../../core/release/services/release.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { environment } from '../../../../environments/environment';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';
import type { TreeResponse, TreeResponseEntry } from '../../../domain/repository/models/tree-response.model';
import type { BranchInfo } from '../../../domain/repository/models/branch-info.model';
import type { BlobResponse } from '../../../domain/repository/models/blob-response.model';
import type { TagInfo } from '../../../domain/repository/models/tag-info.model';
import type { ReleaseInfo } from '../../../domain/release/models/release-info.model';
import { decodeBase64Utf8 } from '../shared/utils/encoding';
import { ToastService } from '../../../core/toast/toast.service';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { postProcessReadmeHtml } from '../shared/readme-markdown.utils';
import { LanguageService } from '../../../core/language/services/language.service';
import { LanguageBarComponent } from '../../../shared/components/language-bar/language-bar.component';
import type { LanguageStats } from '../../../domain/language/models/language-stats.model';

marked.use({ gfm: true, breaks: false });

@Component({
  selector: 'app-repo-home',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, FileSizePipe, RelativeTimePipe, LanguageBarComponent],
  templateUrl: './repo-home.page.html',
  styleUrl: './repo-home.page.css',
})
export class RepoHomePage implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly branchService = inject(BranchService);
  private readonly tagService = inject(TagService);
  private readonly releaseService = inject(ReleaseService);
  readonly repoContext = inject(RepoContextService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly toast = inject(ToastService);
  private readonly languageService = inject(LanguageService);

  readonly tree = signal<TreeResponseEntry[]>([]);
  readonly branches = signal<BranchInfo[]>([]);
  readonly loading = signal(true);
  readonly isEmpty = signal(false);
  readonly selectedBranch = signal('');
  readonly readmeHtml = signal<SafeHtml | null>(null);
  readonly languages = signal<LanguageStats[]>([]);
  readonly allTags = signal<TagInfo[]>([]);
  readonly latestRelease = signal<ReleaseInfo | null>(null);

  readonly recentTags = computed(() => this.allTags().slice(0, 5));

  /** Fullscreen preview for README images (blob or absolute URLs). */
  readonly readmeImageLightboxOpen = signal(false);
  readonly readmeImageLightboxSrc = signal<string | null>(null);
  readonly readmeImageLightboxAlt = signal<string | null>(null);

  private readonly readmeObjectUrls: string[] = [];

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

  readonly sshCloneUrl = computed(() => `ssh://${environment.gitUrl}/${this.owner()}/${this.repoName()}.git`);

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
    this.closeReadmeImageLightbox();
    this.clearReadmeObjectUrls();
  }

  @HostListener('document:keydown.escape', ['$event'])
  onDocumentEscape(event: Event): void {
    if (!this.readmeImageLightboxOpen()) return;
    event.preventDefault();
    this.closeReadmeImageLightbox();
  }

  onReadmeImageClick(event: MouseEvent): void {
    const t = event.target;
    if (!(t instanceof Element)) return;
    const img = t.closest('img');
    if (!img || !(img instanceof HTMLImageElement) || !img.src) return;
    event.preventDefault();
    event.stopPropagation();
    this.readmeImageLightboxSrc.set(img.currentSrc || img.src);
    this.readmeImageLightboxAlt.set(img.alt?.trim() ? img.alt : null);
    this.readmeImageLightboxOpen.set(true);
  }

  closeReadmeImageLightbox(): void {
    this.readmeImageLightboxOpen.set(false);
    this.readmeImageLightboxSrc.set(null);
    this.readmeImageLightboxAlt.set(null);
  }

  onReadmeLightboxBackdropClick(event: MouseEvent): void {
    if (event.target !== event.currentTarget) return;
    this.closeReadmeImageLightbox();
  }

  private clearReadmeObjectUrls(): void {
    for (const u of this.readmeObjectUrls) {
      URL.revokeObjectURL(u);
    }
    this.readmeObjectUrls.length = 0;
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
    this.allTags.set([]);
    this.latestRelease.set(null);
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

      const hasCommits = branchesData.some((b) => b.lastCommitSha !== '');
      if (!hasCommits) {
        this.isEmpty.set(true);
        return;
      }

      await this.loadTreeOnce(owner, repo, branchForTree);
      await Promise.all([
        this.loadReadme(owner, repo, branchForTree),
        this.loadLanguages(owner, repo, branchForTree),
        this.tagService
          .getAll(owner, repo)
          .then((t) => this.allTags.set(t))
          .catch(noop),
        this.releaseService
          .getLatest(owner, repo)
          .then((r) => this.latestRelease.set(r))
          .catch(noop),
      ]);
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
    this.closeReadmeImageLightbox();
    this.clearReadmeObjectUrls();
    this.readmeHtml.set(null);
    const candidates = ['README.md'];
    for (const candidate of candidates) {
      const found = await this.tryFetchReadme(owner, repo, branch, candidate);
      if (found) return;
    }
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
      const processed = await postProcessReadmeHtml(sanitized, {
        readmePath: filename,
        owner,
        repo,
        branch,
        apiUrl: environment.apiUrl,
        http: this.http,
        registerObjectUrl: (u) => this.readmeObjectUrls.push(u),
      });
      this.readmeHtml.set(this.sanitizer.bypassSecurityTrustHtml(processed));
      return true;
    } catch {
      this.clearReadmeObjectUrls();
      return false;
    }
  }

  private async loadLanguages(owner: string, repo: string, branch: string): Promise<void> {
    const stats = await this.languageService.getLanguages(owner, repo, branch);
    this.languages.set(stats);
  }

  onBranchChange(branchName: string): void {
    this.selectedBranch.set(branchName);
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    this.loadTreeOnce(owner, repo, branchName);
    this.loadReadme(owner, repo, branchName);
    void this.loadLanguages(owner, repo, branchName);
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
        // eslint-disable-next-line no-control-regex
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
