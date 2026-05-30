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

import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { MarkdownPipe } from '../../../shared/pipes/markdown.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { ReleaseService } from '../../../core/release/services/release.service';
import { TagService } from '../../../core/tag/services/tag.service';
import { BranchService } from '../../../core/branch/services/branch.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { TagInfo } from '../../../domain/repository/models/tag-info.model';
import type { BranchInfo } from '../../../domain/repository/models/branch-info.model';

@Component({
  selector: 'app-create-release',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, LucideAngularModule, MarkdownPipe],
  templateUrl: './create-release.page.html',
})
export class CreateReleasePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly releaseService = inject(ReleaseService);
  private readonly tagService = inject(TagService);
  private readonly branchService = inject(BranchService);
  readonly repoContext = inject(RepoContextService);
  private readonly toast = inject(ToastService);

  readonly tags = signal<TagInfo[]>([]);
  readonly branches = signal<BranchInfo[]>([]);
  readonly submitting = signal(false);
  readonly createNewTag = signal(false);
  readonly bodyTab = signal<'write' | 'preview'>('write');

  private readonly routeParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.routeParams().get('owner') ?? '');
  readonly repoName = computed(() => this.routeParams().get('repo') ?? '');

  readonly form = this.fb.group({
    tagName: ['', [Validators.required, Validators.pattern(/^[a-zA-Z0-9._\-/]+$/)]],
    name: [''],
    body: [''],
    isDraft: [false],
    isPrerelease: [false],
    targetCommitish: ['main'],
    tagMessage: [''],
  });

  constructor() {
    this.route.parent?.paramMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadMeta());
  }

  ngOnInit(): void {
    const tagFromQuery = this.route.snapshot.queryParamMap.get('tag');
    if (tagFromQuery) {
      this.form.patchValue({ tagName: tagFromQuery });
    }
  }

  private async loadMeta(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    const [tags, branches] = await Promise.all([
      this.tagService.getAll(owner, repo).catch(() => []),
      this.branchService.getAll(owner, repo).catch(() => []),
    ]);
    this.tags.set(tags);
    this.branches.set(branches);
    const defaultBranch = branches.find((b) => b.isDefault)?.name ?? 'main';
    this.form.patchValue({ targetCommitish: defaultBranch });
  }

  toggleCreateNewTag(): void {
    this.createNewTag.update((v) => !v);
    if (!this.createNewTag()) {
      this.form.patchValue({ targetCommitish: '' });
    }
  }

  async submit(publish: boolean): Promise<void> {
    if (this.form.invalid) return;
    this.submitting.set(true);
    const val = this.form.value;
    try {
      const release = await this.releaseService.create(this.owner(), this.repoName(), {
        tagName: val.tagName!,
        name: val.name || undefined,
        body: val.body || undefined,
        isDraft: !publish,
        isPrerelease: val.isPrerelease ?? false,
        createNewTag: this.createNewTag(),
        targetCommitish: val.targetCommitish || undefined,
        tagMessage: val.tagMessage || undefined,
      });
      this.toast.success(publish ? 'Release published' : 'Draft saved');
      await this.router.navigate(['/', this.owner(), this.repoName(), 'releases', 'tag', release.tagName]);
    } catch {
      this.toast.error('Could not create release');
    } finally {
      this.submitting.set(false);
    }
  }
}
