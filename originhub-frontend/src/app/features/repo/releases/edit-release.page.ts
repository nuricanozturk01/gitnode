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

import { Component, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { MarkdownPipe } from '../../../shared/pipes/markdown.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { ReleaseService } from '../../../core/release/services/release.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { ReleaseInfo } from '../../../domain/release/models/release-info.model';

@Component({
  selector: 'app-edit-release',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, LucideAngularModule, MarkdownPipe],
  templateUrl: './edit-release.page.html',
})
export class EditReleasePage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly releaseService = inject(ReleaseService);
  readonly repoContext = inject(RepoContextService);
  private readonly toast = inject(ToastService);

  readonly release = signal<ReleaseInfo | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly bodyTab = signal<'write' | 'preview'>('write');

  private readonly routeParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.routeParams().get('owner') ?? '');
  readonly repoName = computed(() => this.routeParams().get('repo') ?? '');

  readonly form = this.fb.group({
    name: [''],
    body: [''],
    isDraft: [false],
    isPrerelease: [false],
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = params.get('id');
      if (id) void this.loadRelease(id);
    });
  }

  private async loadRelease(id: string): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    this.loading.set(true);
    try {
      const release = await this.releaseService.getById(owner, repo, id);
      this.release.set(release);
      this.form.patchValue({
        name: release.name ?? '',
        body: release.body ?? '',
        isDraft: release.isDraft,
        isPrerelease: release.isPrerelease,
      });
    } catch {
      this.toast.error('Could not load release');
      await this.router.navigate(['/', owner, repo, 'releases']);
    } finally {
      this.loading.set(false);
    }
  }

  async save(publish: boolean): Promise<void> {
    const r = this.release();
    if (!r || this.form.invalid) return;
    this.submitting.set(true);
    const val = this.form.value;
    try {
      await this.releaseService.update(this.owner(), this.repoName(), r.id, {
        name: val.name || undefined,
        body: val.body || undefined,
        isDraft: !publish,
        isPrerelease: val.isPrerelease ?? false,
      });
      this.toast.success(publish ? 'Release published' : 'Draft saved');
      await this.router.navigate(['/', this.owner(), this.repoName(), 'releases', 'tag', r.tagName]);
    } catch {
      this.toast.error('Could not update release');
    } finally {
      this.submitting.set(false);
    }
  }
}
