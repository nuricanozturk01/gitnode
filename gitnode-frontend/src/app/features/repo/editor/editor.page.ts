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

import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Router } from '@angular/router';
import { DomSanitizer, type SafeHtml } from '@angular/platform-browser';
import type { HLJSApi } from 'highlight.js';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { ModalFocusDirective } from '../../../shared/directives/modal-focus.directive';
import { grandParentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { ActivatedRoute } from '@angular/router';
import { EditorService, type EditorChange } from '../../../core/editor/services/editor.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { TreeResponseEntry } from '../../../domain/repository/models/tree-response.model';

interface OpenFile {
  path: string;
  /** Content at the base commit, or null for a file created in this session. */
  original: string | null;
  content: string;
  deleted: boolean;
  editable: boolean;
  reason?: string;
  /** Set when the file was created by renaming this path. */
  renamedFrom?: string;
}

const MAX_EDITABLE_BYTES = 512 * 1024;
const MAX_HIGHLIGHT_CHARS = 200_000;

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function validPath(path: string): boolean {
  return !path.split('/').some((s) => !s || s === '.' || s === '..' || s.toLowerCase() === '.git');
}

function decodeBase64Utf8(value: string): string {
  const bytes = Uint8Array.from(atob(value), (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/** Lightweight multi-file web editor opened with the "." shortcut. Changes are committed as one commit. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-editor',
  standalone: true,
  imports: [FormsModule, LucideAngularModule, NgTemplateOutlet, ModalFocusDirective],
  templateUrl: './editor.page.html',
  styleUrl: './editor.page.css',
})
export class EditorPage implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(EditorService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly repoParams = grandParentParamMapSignal(this.route);

  readonly owner = computed(() => this.repoParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoParams().get('repo') ?? '');
  readonly branch = signal('');
  readonly headSha = signal('');
  readonly loading = signal(true);
  readonly loadFailed = signal(false);

  readonly expanded = signal<ReadonlySet<string>>(new Set());
  readonly children = signal<Record<string, TreeResponseEntry[]>>({});
  readonly files = signal<Record<string, OpenFile>>({});
  readonly openPaths = signal<string[]>([]);
  readonly activePath = signal<string | null>(null);
  readonly treeOpen = signal(true);

  readonly commitOpen = signal(false);
  readonly message = signal('');
  readonly description = signal('');
  readonly committing = signal(false);
  readonly commitError = signal<string | null>(null);
  readonly stale = signal(false);

  readonly active = computed(() => {
    const path = this.activePath();
    return path ? (this.files()[path] ?? null) : null;
  });
  private readonly hljs = signal<HLJSApi | null>(null);
  /** Syntax-highlighted copy of the active file, drawn behind the transparent textarea. */
  readonly highlighted = computed<SafeHtml>(() => {
    const file = this.active();
    const hljs = this.hljs();
    const content = file?.content ?? '';
    let html = escapeHtml(content);
    if (file && hljs && content.length <= MAX_HIGHLIGHT_CHARS) {
      const language = this.languageOf(file.path, hljs);
      if (language) html = hljs.highlight(content, { language, ignoreIllegals: true }).value;
    }
    // A trailing newline would collapse; keep the overlay exactly as tall as the textarea.
    return this.sanitizer.bypassSecurityTrustHtml(html + '\n');
  });
  readonly changes = computed(() => this.computeChanges());
  readonly dirty = computed(() => this.changes().length > 0);
  readonly rootEntries = computed(() => this.children()[''] ?? []);

  private beforeUnload = (event: BeforeUnloadEvent): void => {
    if (this.dirty()) event.preventDefault();
  };

  constructor() {
    window.addEventListener('beforeunload', this.beforeUnload);
    effect(() => {
      if (this.owner() && this.repoName()) void this.start();
    });
    void import('highlight.js').then((m) => this.hljs.set(m.default));
  }

  private languageOf(path: string, hljs: HLJSApi): string | null {
    const name = this.baseName(path).toLowerCase();
    const ext = name.includes('.') ? name.slice(name.lastIndexOf('.') + 1) : name;
    const aliases: Record<string, string> = { dockerfile: 'dockerfile', makefile: 'makefile', tsx: 'typescript', jsx: 'javascript', mjs: 'javascript', cjs: 'javascript', yml: 'yaml', htm: 'xml', html: 'xml', svg: 'xml', vue: 'xml' };
    const candidate = aliases[ext] ?? ext;
    return hljs.getLanguage(candidate) ? candidate : null;
  }

  onEditorScroll(event: Event, overlay: HTMLElement): void {
    const el = event.target as HTMLTextAreaElement;
    overlay.scrollTop = el.scrollTop;
    overlay.scrollLeft = el.scrollLeft;
  }

  ngOnDestroy(): void {
    window.removeEventListener('beforeunload', this.beforeUnload);
  }

  private computeChanges(): EditorChange[] {
    const result: EditorChange[] = [];
    for (const file of Object.values(this.files())) {
      if (file.deleted) {
        if (file.original !== null) result.push({ path: file.path, delete: true });
      } else if (file.original === null) {
        result.push({ path: file.path, content: file.content, delete: false });
      } else if (file.editable && file.content !== file.original) {
        result.push({ path: file.path, content: file.content, delete: false });
      }
    }
    return result.sort((a, b) => a.path.localeCompare(b.path));
  }

  isChanged(path: string): boolean {
    return this.changes().some((c) => c.path === path);
  }

  private async start(): Promise<void> {
    const match = this.router.url.match(/\/editor\/(.+?)(?:[?#].*)?$/);
    const parts = (match?.[1] ?? '').split('/').filter(Boolean).map(decodeURIComponent);
    const branch = parts[0] ?? '';
    const initial = parts.slice(1).join('/');
    this.branch.set(branch);
    this.loading.set(true);
    this.loadFailed.set(false);
    try {
      const [head, root] = await Promise.all([
        this.service.headSha(this.owner(), this.repoName(), branch),
        this.service.tree(this.owner(), this.repoName(), branch, ''),
      ]);
      this.headSha.set(head);
      this.children.set({ '': root.entries });
      this.files.set({});
      this.openPaths.set([]);
      this.activePath.set(null);
      this.stale.set(false);
      if (initial) await this.openFile(initial);
    } catch {
      this.loadFailed.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  retry(): void {
    void this.start();
  }

  async toggleDir(entry: TreeResponseEntry): Promise<void> {
    const open = new Set(this.expanded());
    if (open.has(entry.path)) {
      open.delete(entry.path);
      this.expanded.set(open);
      return;
    }
    open.add(entry.path);
    this.expanded.set(open);
    if (!this.children()[entry.path]) {
      try {
        const tree = await this.service.tree(this.owner(), this.repoName(), this.branch(), entry.path);
        this.children.update((c) => ({ ...c, [entry.path]: tree.entries }));
      } catch {
        this.toast.error(`Could not open ${entry.path}`);
      }
    }
  }

  async openFile(path: string): Promise<void> {
    if (!this.files()[path]) {
      try {
        const blob = await this.service.blob(this.owner(), this.repoName(), this.branch(), path);
        const tooBig = blob.size > MAX_EDITABLE_BYTES;
        const editable = !blob.isBinary && !tooBig;
        this.files.update((f) => ({
          ...f,
          [path]: {
            path,
            original: editable ? decodeBase64Utf8(blob.content) : '',
            content: editable ? decodeBase64Utf8(blob.content) : '',
            deleted: false,
            editable,
            reason: blob.isBinary ? 'Binary files cannot be edited here.' : tooBig ? 'This file is too large to edit here.' : undefined,
          },
        }));
      } catch {
        this.toast.error(`Could not open ${path}`);
        return;
      }
    }
    this.openPaths.update((p) => (p.includes(path) ? p : [...p, path]));
    this.activePath.set(path);
  }

  setContent(value: string): void {
    const path = this.activePath();
    if (!path) return;
    this.files.update((f) => ({ ...f, [path]: { ...f[path], content: value } }));
  }

  closeTab(path: string): void {
    const file = this.files()[path];
    if (file && !file.deleted && file.original !== null && file.content === file.original) {
      this.files.update((f) => {
        const { [path]: _removed, ...rest } = f;
        return rest;
      });
    }
    const remaining = this.openPaths().filter((p) => p !== path);
    this.openPaths.set(remaining);
    if (this.activePath() === path) this.activePath.set(remaining[remaining.length - 1] ?? null);
  }

  async newFile(): Promise<void> {
    const path = window.prompt('New file path (for example src/new.txt)')?.trim().replace(/^\/+/, '');
    if (!path) return;
    if (!validPath(path)) {
      this.toast.error('That is not a valid file path');
      return;
    }
    if (this.files()[path] && !this.files()[path].deleted) {
      this.activePath.set(path);
      return;
    }
    this.files.update((f) => ({ ...f, [path]: { path, original: null, content: '', deleted: false, editable: true } }));
    this.openPaths.update((p) => (p.includes(path) ? p : [...p, path]));
    this.activePath.set(path);
  }

  /** Renames the active file: the old path is deleted and the new path added in the same commit. */
  renameActive(): void {
    const file = this.active();
    if (!file || !file.editable) return;
    const target = window.prompt('New path for this file', file.path)?.trim().replace(/^\/+/, '');
    if (!target || target === file.path) return;
    if (!validPath(target)) {
      this.toast.error('That is not a valid file path');
      return;
    }
    const existing = this.files()[target];
    if ((existing && !existing.deleted) || this.knownPaths().has(target)) {
      this.toast.error(`${target} already exists`);
      return;
    }
    const renamedFrom = file.renamedFrom ?? (file.original === null ? undefined : file.path);
    this.files.update((f) => {
      const next = { ...f };
      if (file.original === null) delete next[file.path];
      else next[file.path] = { ...file, deleted: true };
      next[target] = { path: target, original: null, content: file.content, deleted: false, editable: true, renamedFrom };
      return next;
    });
    this.openPaths.update((p) => [...p.filter((x) => x !== file.path), target]);
    this.activePath.set(target);
  }

  private knownPaths(): Set<string> {
    const known = new Set<string>();
    for (const entries of Object.values(this.children())) for (const e of entries) if (e.type !== 'TREE') known.add(e.path);
    for (const f of Object.values(this.files())) if (f.original !== null && !f.deleted) known.add(f.path);
    return known;
  }

  async deleteActive(): Promise<void> {
    const file = this.active();
    if (!file) return;
    if (!(await this.confirmModal.confirm('Delete file', `Delete ${file.path}?`, { confirmLabel: 'Delete', variant: 'danger' }))) return;
    if (file.original === null) {
      this.files.update((f) => {
        const { [file.path]: _removed, ...rest } = f;
        return rest;
      });
    } else {
      this.files.update((f) => ({ ...f, [file.path]: { ...f[file.path], deleted: true } }));
    }
    const remaining = this.openPaths().filter((p) => p !== file.path);
    this.openPaths.set(remaining);
    this.activePath.set(remaining[remaining.length - 1] ?? null);
  }

  revert(path: string): void {
    const file = this.files()[path];
    if (!file) return;
    if (file.original === null) {
      this.closeTab(path);
      this.files.update((f) => {
        const { [path]: _removed, ...rest } = f;
        return rest;
      });
      return;
    }
    this.files.update((f) => ({ ...f, [path]: { ...file, content: file.original ?? '', deleted: false } }));
  }

  openCommit(): void {
    if (!this.dirty()) return;
    this.commitError.set(null);
    this.commitOpen.set(true);
    if (!this.message()) {
      const changes = this.changes();
      this.message.set(changes.length === 1 ? `Update ${changes[0].path}` : `Update ${changes.length} files`);
    }
  }

  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
      event.preventDefault();
      this.openCommit();
    }
    if (event.key === 'Escape' && this.commitOpen() && !this.committing()) this.commitOpen.set(false);
  }

  async commit(): Promise<void> {
    const message = this.message().trim();
    if (!message || this.committing() || !this.dirty()) return;
    this.committing.set(true);
    this.commitError.set(null);
    try {
      const result = await this.service.commit(this.owner(), this.repoName(), {
        branch: this.branch(),
        expectedHeadSha: this.headSha(),
        message,
        description: this.description().trim() || undefined,
        changes: this.changes(),
      });
      this.headSha.set(result.commitSha);
      this.files.update((f) => {
        const next: Record<string, OpenFile> = {};
        for (const file of Object.values(f)) {
          if (file.deleted) continue;
          next[file.path] = { ...file, original: file.content };
        }
        return next;
      });
      this.openPaths.update((paths) => paths.filter((p) => !!this.files()[p]));
      if (this.activePath() && !this.files()[this.activePath()!]) this.activePath.set(this.openPaths()[0] ?? null);
      this.children.set({});
      this.expanded.set(new Set());
      const root = await this.service.tree(this.owner(), this.repoName(), this.branch(), '');
      this.children.set({ '': root.entries });
      this.message.set('');
      this.description.set('');
      this.commitOpen.set(false);
      this.toast.success('Changes committed');
    } catch (error) {
      const text = (error as { error?: { message?: string } })?.error?.message;
      this.stale.set(!!text && /changed/i.test(text));
      this.commitError.set(text ?? 'Could not commit the changes.');
    } finally {
      this.committing.set(false);
    }
  }

  async exit(): Promise<void> {
    if (this.dirty() && !(await this.confirmModal.confirm('Discard changes', 'You have uncommitted changes. Leave the editor and discard them?', { confirmLabel: 'Discard', variant: 'danger' }))) return;
    this.dirty(); // keep signal read for lint
    await this.router.navigate(['/', this.owner(), this.repoName(), 'tree', this.branch()]);
  }

  async reloadLatest(): Promise<void> {
    if (!(await this.confirmModal.confirm('Reload editor', 'Discard your edits and load the latest version of the branch?', { confirmLabel: 'Reload', variant: 'danger' }))) return;
    this.commitOpen.set(false);
    await this.start();
  }

  lineCount(content: string): number {
    return Math.max(1, content.split('\n').length);
  }

  baseName(path: string): string {
    return path.split('/').pop() ?? path;
  }
}
