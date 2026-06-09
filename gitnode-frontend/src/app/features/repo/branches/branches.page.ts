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

import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { BranchService } from '../../../core/branch/services/branch.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { BranchInfo } from '../../../domain/repository/models/branch-info.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-branches',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './branches.page.html',
  styleUrl: './branches.page.css',
})
export class BranchesPage {
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly branchService = inject(BranchService);
  private readonly repoContext = inject(RepoContextService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly branches = signal<BranchInfo[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly createModalOpen = signal(false);
  readonly setDefaultModalOpen = signal(false);
  readonly branchToSetDefault = signal<BranchInfo | null>(null);
  readonly actionLoading = signal(false);

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');
  readonly defaultBranch = this.repoContext.defaultBranch;
  readonly canEdit = this.repoContext.canEdit;

  readonly createForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(/^[a-zA-Z0-9_\-/.]+$/)]],
    sourceBranch: ['main', [Validators.required]],
  });

  constructor() {
    this.route.parent!.paramMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadBranches());
  }

  async loadBranches(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      const data = await this.branchService.getAll(owner, repo);
      this.branches.set(data);
    } catch {
      this.error.set('Failed to load branches');
      this.branches.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  openCreateModal(): void {
    this.createForm.patchValue({ sourceBranch: this.defaultBranch() });
    this.createModalOpen.set(true);
  }

  closeCreateModal(): void {
    this.createModalOpen.set(false);
    this.createForm.reset({ name: '', sourceBranch: this.defaultBranch() });
  }

  async onCreateSubmit(): Promise<void> {
    if (this.createForm.invalid) return;
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    this.actionLoading.set(true);
    this.error.set(null);
    try {
      await this.branchService.createBranch(owner, repo, this.createForm.getRawValue());
      this.toast.success('Branch created successfully');
      this.closeCreateModal();
      await this.loadBranches();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to create branch';
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.actionLoading.set(false);
    }
  }

  openSetDefaultModal(branch: BranchInfo): void {
    this.branchToSetDefault.set(branch);
    this.setDefaultModalOpen.set(true);
  }

  closeSetDefaultModal(): void {
    this.setDefaultModalOpen.set(false);
    this.branchToSetDefault.set(null);
  }

  async onSetDefaultConfirm(): Promise<void> {
    const branch = this.branchToSetDefault();
    if (!branch) return;
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    this.actionLoading.set(true);
    this.error.set(null);
    try {
      await this.branchService.setDefaultBranch(owner, repo, { branchName: branch.name });
      this.toast.success('Default branch updated');
      this.closeSetDefaultModal();
      await this.loadBranches();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to set default branch';
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.actionLoading.set(false);
    }
  }

  async deleteBranch(branch: BranchInfo): Promise<void> {
    if (branch.isDefault) return;
    const ok = await this.confirmModal.confirm(`Delete branch "${branch.name}"?`, undefined, {
      variant: 'danger',
    });
    if (!ok) return;
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    this.actionLoading.set(true);
    this.error.set(null);
    try {
      await this.branchService.deleteBranch(owner, repo, branch.name);
      this.toast.success('Branch deleted');
      await this.loadBranches();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to delete branch';
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.actionLoading.set(false);
    }
  }
}
