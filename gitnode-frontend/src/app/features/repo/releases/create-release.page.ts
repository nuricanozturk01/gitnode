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

import { Component, ChangeDetectionStrategy, inject, signal, computed, OnInit } from '@angular/core';
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
  changeDetection: ChangeDetectionStrategy.OnPush,
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
  readonly generating = signal(false);
  readonly createNewTag = signal(false);
  readonly metaLoading = signal(true);
  readonly metaFailed = signal(false);
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
    this.metaLoading.set(true);
    this.metaFailed.set(false);
    try {
      const [tags, branches] = await Promise.all([
        this.tagService.getAll(owner, repo),
        this.branchService.getAll(owner, repo),
      ]);
      const committedBranches = branches.filter((branch) => !!branch.lastCommitSha);
      this.tags.set(tags);
      this.branches.set(committedBranches);
      this.createNewTag.set(tags.length === 0);
      const defaultBranch = committedBranches.find((b) => b.isDefault)?.name ?? committedBranches[0]?.name ?? '';
      this.form.patchValue({ targetCommitish: defaultBranch });
    } catch {
      this.metaFailed.set(true);
    } finally {
      this.metaLoading.set(false);
    }
  }

  retryMeta(): void {
    void this.loadMeta();
  }

  toggleCreateNewTag(): void {
    this.createNewTag.update((v) => !v);
  }

  async generateNotes(): Promise<void> {
    const tagName = (this.form.value.tagName ?? '').trim();
    if (!tagName || this.generating()) return;
    this.generating.set(true);
    try {
      const target = this.createNewTag() ? this.form.value.targetCommitish || undefined : undefined;
      const notes = await this.releaseService.generateNotes(this.owner(), this.repoName(), tagName, target);
      this.form.patchValue({ body: notes.body });
      if (!this.form.value.name) this.form.patchValue({ name: notes.name });
      this.bodyTab.set('write');
      this.toast.success(
        notes.commitCount === 0
          ? 'No new commits since the previous tag'
          : `Generated notes from ${notes.commitCount} commits`,
      );
    } catch {
      this.toast.error('Could not generate release notes. Check the tag and target branch.');
    } finally {
      this.generating.set(false);
    }
  }

  async submit(publish: boolean): Promise<void> {
    if (
      this.form.invalid ||
      this.metaLoading() ||
      this.metaFailed() ||
      this.submitting() ||
      (this.createNewTag() && !this.form.value.targetCommitish)
    )
      return;
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
