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

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { SnippetService } from '../../../core/snippet/services/snippet.service';
import { RepoService } from '../../../core/repo/services/repo.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { ToastService } from '../../../core/toast/toast.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import type { SnippetVisibility } from '../../../domain/snippet/models/snippet.model';
import type { RepoInfo } from '../../../domain/repository/models/repo-info.model';

interface FileRow {
  filename: string;
  content: string;
}

@Component({
  selector: 'app-snippet-edit',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './snippet-edit.page.html',
})
export class SnippetEditPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snippetService = inject(SnippetService);
  private readonly repoService = inject(RepoService);
  private readonly tokenService = inject(TokenService);
  private readonly toastService = inject(ToastService);
  private readonly confirmModal = inject(ConfirmModalService);

  readonly snippetId = signal<string | null>(null);
  readonly isEditMode = signal(false);
  readonly loading = signal(false);
  readonly saving = signal(false);

  readonly title = signal('');
  readonly description = signal('');
  readonly visibility = signal<SnippetVisibility>('PUBLIC');
  readonly files = signal<FileRow[]>([{ filename: 'snippet.txt', content: '' }]);
  readonly summary = signal('');
  readonly linkedRepoIds = signal<Set<string>>(new Set());
  readonly initialRepoIds = signal<Set<string>>(new Set());
  readonly repos = signal<RepoInfo[]>([]);

  readonly repoSearch = signal('');
  readonly repoDropdownOpen = signal(false);
  readonly repoDropdownPage = signal(0);
  readonly REPO_PAGE_SIZE = 8;

  readonly filteredRepos = computed(() => {
    const q = this.repoSearch().toLowerCase();
    if (!q) return this.repos();
    return this.repos().filter((r) => r.name.toLowerCase().includes(q));
  });

  readonly repoTotalPages = computed(() => Math.max(1, Math.ceil(this.filteredRepos().length / this.REPO_PAGE_SIZE)));

  readonly pagedRepos = computed(() => {
    const start = this.repoDropdownPage() * this.REPO_PAGE_SIZE;
    return this.filteredRepos().slice(start, start + this.REPO_PAGE_SIZE);
  });

  readonly linkedRepoInfos = computed(() => {
    const ids = this.linkedRepoIds();
    return this.repos().filter((r) => ids.has(r.id));
  });

  ngOnInit(): void {
    void this.loadRepos();
    const id = this.route.snapshot.paramMap.get('snippetId');
    if (id) {
      this.snippetId.set(id);
      this.isEditMode.set(true);
      this.loadExisting(id);
    }
  }

  private async loadRepos(): Promise<void> {
    const username = this.tokenService.getUsername();
    if (!username) return;
    try {
      const page = await this.repoService.listUserRepos(username, 0, 100);
      this.repos.set(page.content);
    } catch {
      // repos are optional
    }
  }

  private async loadExisting(id: string): Promise<void> {
    this.loading.set(true);
    try {
      const detail = await this.snippetService.get(id);
      this.title.set(detail.title);
      this.description.set(detail.description ?? '');
      this.visibility.set(detail.visibility);
      this.files.set(detail.files.map((f) => ({ filename: f.filename, content: f.content })));
      const ids = new Set(detail.repos.map((r) => r.id));
      this.linkedRepoIds.set(ids);
      this.initialRepoIds.set(new Set(ids));
    } catch {
      this.toastService.error('Failed to load snippet');
      this.router.navigate(['/snippets']);
    } finally {
      this.loading.set(false);
    }
  }

  onTitleInput(event: Event): void {
    this.title.set((event.target as HTMLInputElement).value);
  }

  onDescriptionInput(event: Event): void {
    this.description.set((event.target as HTMLTextAreaElement).value);
  }

  onVisibilityChange(v: SnippetVisibility): void {
    this.visibility.set(v);
  }

  onSummaryInput(event: Event): void {
    this.summary.set((event.target as HTMLInputElement).value);
  }

  onRepoToggle(repoId: string): void {
    this.linkedRepoIds.update((ids) => {
      const next = new Set(ids);
      if (next.has(repoId)) {
        next.delete(repoId);
      } else {
        next.add(repoId);
      }
      return next;
    });
  }

  onRepoSearchInput(event: Event): void {
    this.repoSearch.set((event.target as HTMLInputElement).value);
    this.repoDropdownPage.set(0);
  }

  openRepoDropdown(): void {
    this.repoDropdownOpen.set(true);
  }

  closeRepoDropdown(): void {
    this.repoDropdownOpen.set(false);
  }

  keepDropdownOpen(event: MouseEvent): void {
    event.preventDefault();
  }

  repoPrevPage(): void {
    this.repoDropdownPage.update((p) => Math.max(0, p - 1));
  }

  repoNextPage(): void {
    this.repoDropdownPage.update((p) => Math.min(this.repoTotalPages() - 1, p + 1));
  }

  onFilenameInput(index: number, event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    this.files.update((rows) => rows.map((r, i) => (i === index ? { ...r, filename: val } : r)));
  }

  onContentInput(index: number, event: Event): void {
    const val = (event.target as HTMLTextAreaElement).value;
    this.files.update((rows) => rows.map((r, i) => (i === index ? { ...r, content: val } : r)));
  }

  uploadFile(index: number): void {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '*/*';
    input.onchange = (event: Event) => {
      const file = (event.target as HTMLInputElement).files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = (e) => {
        const content = e.target?.result as string;
        this.files.update((rows) => rows.map((r, i) => (i === index ? { filename: file.name, content } : r)));
      };
      reader.onerror = () => {
        this.toastService.error(`Failed to read file: ${file.name}`);
      };
      reader.readAsText(file, 'UTF-8');
    };
    input.click();
  }

  addFile(): void {
    this.files.update((rows) => [...rows, { filename: `file${rows.length + 1}.txt`, content: '' }]);
  }

  removeFile(index: number): void {
    if (this.files().length <= 1) return;
    this.files.update((rows) => rows.filter((_, i) => i !== index));
  }

  async save(): Promise<void> {
    if (!this.title().trim()) {
      this.toastService.error('Title is required');
      return;
    }
    if (this.files().some((f) => !f.filename.trim() || !f.content.trim())) {
      this.toastService.error('All files need a filename and content');
      return;
    }

    this.saving.set(true);
    try {
      let id: string;
      if (this.isEditMode() && this.snippetId()) {
        const updated = await this.snippetService.update(this.snippetId()!, {
          title: this.title(),
          description: this.description() || undefined,
          visibility: this.visibility(),
          files: this.files().map((f) => ({ filename: f.filename, content: f.content })),
          summary: this.summary() || undefined,
        });
        id = updated.id;
        this.toastService.success('Snippet updated');
      } else {
        const created = await this.snippetService.create({
          title: this.title(),
          description: this.description() || undefined,
          visibility: this.visibility(),
          files: this.files().map((f) => ({ filename: f.filename, content: f.content })),
        });
        id = created.id;
        this.toastService.success('Snippet created');
      }
      const current = this.linkedRepoIds();
      const initial = this.initialRepoIds();
      const toAdd = [...current].filter((rid) => !initial.has(rid));
      const toRemove = [...initial].filter((rid) => !current.has(rid));
      await Promise.all([
        ...toAdd.map((rid) => this.snippetService.linkRepo(id, rid)),
        ...toRemove.map((rid) =>
          this.snippetService.unlinkRepo(id, rid).catch(() => {
            // continue
          }),
        ),
      ]);
      this.router.navigate(['/snippets', id]);
    } catch {
      this.toastService.error(this.isEditMode() ? 'Failed to update snippet' : 'Failed to create snippet');
    } finally {
      this.saving.set(false);
    }
  }

  async cancel(): Promise<void> {
    if (this.isEditMode()) {
      this.router.navigate(['/snippets', this.snippetId()]);
    } else {
      const ok = await this.confirmModal.confirm('Discard snippet?', 'Your unsaved snippet will be lost.', {
        confirmLabel: 'Discard',
        variant: 'danger',
      });
      if (ok) this.router.navigate(['/snippets']);
    }
  }
}
