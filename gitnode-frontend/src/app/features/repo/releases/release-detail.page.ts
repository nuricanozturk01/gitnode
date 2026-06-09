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
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  SecurityContext,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { finalize } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { ReleaseService } from '../../../core/release/services/release.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import { environment } from '../../../../environments/environment';
import type { ReleaseInfo } from '../../../domain/release/models/release-info.model';

marked.use({ gfm: true, breaks: false });

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-release-detail',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './release-detail.page.html',
})
export class ReleaseDetailPage {
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly releaseService = inject(ReleaseService);
  readonly repoContext = inject(RepoContextService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly release = signal<ReleaseInfo | null>(null);
  readonly loading = signal(true);
  readonly zipDownloading = signal(false);
  readonly bodyHtml = signal<SafeHtml | null>(null);

  private readonly routeParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.routeParams().get('owner') ?? '');
  readonly repoName = computed(() => this.routeParams().get('repo') ?? '');

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const tagName = params.get('tagName');
      if (tagName) void this.loadRelease(tagName);
    });
  }

  private async loadRelease(tagName: string): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    this.loading.set(true);
    try {
      const release = await this.releaseService.getByTag(owner, repo, tagName);
      this.release.set(release);
      if (release.body) {
        const html = await marked.parse(release.body);
        const sanitized = this.sanitizer.sanitize(SecurityContext.HTML, html) ?? '';
        this.bodyHtml.set(this.sanitizer.bypassSecurityTrustHtml(sanitized));
      }
    } catch {
      this.release.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  downloadZip(): void {
    const r = this.release();
    const owner = this.owner();
    const repo = this.repoName();
    if (!r || !owner || !repo) return;

    const url = `${environment.apiUrl}/api/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/archive/${encodeURIComponent(r.tagName)}`;
    this.zipDownloading.set(true);
    this.http
      .get(url, { responseType: 'blob', observe: 'response' })
      .pipe(
        finalize(() => this.zipDownloading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (resp) => {
          const blob = resp.body;
          if (!blob) {
            this.toast.error('Download failed');
            return;
          }
          const cd = resp.headers.get('Content-Disposition') ?? '';
          const match = /filename\*?=(?:UTF-8'')?["']?([^"';\n]+)/i.exec(cd);
          const fileName = match ? decodeURIComponent(match[1]) : `${owner}-${repo}-${r.tagName}.zip`;
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

  async deleteRelease(): Promise<void> {
    const r = this.release();
    if (!r) return;
    const confirmed = await this.confirmModal.confirm(
      'Delete release',
      `Delete release "${r.name ?? r.tagName}"? The tag will remain.`,
      { variant: 'danger' },
    );
    if (!confirmed) return;
    try {
      await this.releaseService.delete(this.owner(), this.repoName(), r.id);
      this.toast.success('Release deleted');
      await this.router.navigate(['/', this.owner(), this.repoName(), 'releases']);
    } catch {
      this.toast.error('Could not delete release');
    }
  }
}
