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
  Component,
  ChangeDetectionStrategy,
  OnInit,
  SecurityContext,
  ViewEncapsulation,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { LucideAngularModule } from 'lucide-angular';
import hljs from 'highlight.js';
import { marked } from 'marked';
import { SnippetService } from '../../../core/snippet/services/snippet.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { ToastService } from '../../../core/toast/toast.service';
import { copyTextToClipboard } from '../../../shared/utils/clipboard.util';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ThemeService } from '../../../core/theme/theme.service';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { filenameToHljsLanguage, isMarkdown } from '../shared/language-detect.util';
import type {
  SnippetDetail,
  SnippetCommentInfo,
  SnippetRevisionInfo,
  SnippetFileInfo,
} from '../../../domain/snippet/models/snippet.model';

interface HighlightedFile {
  file: SnippetFileInfo;
  language: string;
  lines: SafeHtml[];
  isMarkdown: boolean;
  markdownHtml: SafeHtml | null;
  collapsed: boolean;
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-snippet-detail',
  standalone: true,
  encapsulation: ViewEncapsulation.None,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './snippet-detail.page.html',
  styleUrl: './snippet-detail.page.css',
})
export class SnippetDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snippetService = inject(SnippetService);
  private readonly tokenService = inject(TokenService);
  private readonly toastService = inject(ToastService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly sanitizer = inject(DomSanitizer);
  readonly theme = inject(ThemeService);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly snippet = signal<SnippetDetail | null>(null);
  readonly highlightedFiles = signal<HighlightedFile[]>([]);

  readonly comments = signal<SnippetCommentInfo[]>([]);
  readonly commentPage = signal(0);
  readonly commentTotalPages = signal(0);
  readonly commentTotalElements = signal(0);
  readonly loadingComments = signal(false);

  readonly revisions = signal<SnippetRevisionInfo[]>([]);
  readonly revisionPage = signal(0);
  readonly revisionTotalPages = signal(0);
  readonly loadingRevisions = signal(false);

  readonly commentBody = signal('');
  readonly submittingComment = signal(false);
  readonly snippetId = signal('');

  readonly blobSyntaxTheme = computed(() => (this.theme.isDark() ? 'dark' : 'light'));
  readonly currentUsername = this.tokenService.getUsername();
  readonly isOwner = computed(() => {
    const s = this.snippet();
    return s != null && s.owner.username === this.currentUsername;
  });
  readonly isLoggedIn = computed(() => this.tokenService.getAccessToken() != null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('snippetId') ?? '';
    this.snippetId.set(id);
    this.loadAll(id);
  }

  private async loadAll(id: string): Promise<void> {
    this.loading.set(true);
    this.loadError.set(null);
    try {
      const [detail, commentPage, revisionPage] = await Promise.all([
        this.snippetService.get(id),
        this.snippetService.listComments(id, 0, 10),
        this.snippetService.listRevisions(id, 0, 10),
      ]);
      this.snippet.set(detail);
      this.comments.set(commentPage.content);
      this.commentPage.set(commentPage.number);
      this.commentTotalPages.set(commentPage.totalPages);
      this.commentTotalElements.set(commentPage.totalElements);
      this.revisions.set(revisionPage.content);
      this.revisionPage.set(revisionPage.number);
      this.revisionTotalPages.set(revisionPage.totalPages);
      await this.buildHighlights(detail);
    } catch {
      const message = 'Snippet not found or you do not have access.';
      this.loadError.set(message);
      this.toastService.error(message);
      if (this.isLoggedIn()) {
        this.router.navigate(['/snippets']);
      }
    } finally {
      this.loading.set(false);
    }
  }

  private async buildHighlights(detail: SnippetDetail): Promise<void> {
    const result: HighlightedFile[] = [];
    marked.use({ gfm: true, breaks: false });

    for (const file of detail.files) {
      const lang = filenameToHljsLanguage(file.filename);
      const md = isMarkdown(file.filename);

      if (md) {
        const html = await marked.parse(file.content);
        const sanitized = this.sanitizer.sanitize(SecurityContext.HTML, html) ?? '';
        result.push({
          file,
          language: lang,
          lines: [],
          isMarkdown: true,
          markdownHtml: this.sanitizer.bypassSecurityTrustHtml(sanitized),
          collapsed: false,
        });
      } else {
        let highlighted: string;
        if (lang !== 'plaintext' && hljs.getLanguage(lang)) {
          highlighted = hljs.highlight(file.content, { language: lang, ignoreIllegals: true }).value;
        } else {
          highlighted = file.content.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }
        const lines = highlighted.split('\n').map((line) => this.sanitizer.bypassSecurityTrustHtml(line || '&nbsp;'));
        result.push({
          file,
          language: lang,
          lines,
          isMarkdown: false,
          markdownHtml: null,
          collapsed: false,
        });
      }
    }

    this.highlightedFiles.set(result);
  }

  toggleCollapse(index: number): void {
    this.highlightedFiles.update((files) => files.map((f, i) => (i === index ? { ...f, collapsed: !f.collapsed } : f)));
  }

  copyToClipboard(text: string): void {
    void copyTextToClipboard(text).then(() => {
      this.toastService.success('Copied to clipboard');
    });
  }

  copySnippetUrl(): void {
    this.copyToClipboard(window.location.href);
  }

  rawFileUrl(fileId: string): string {
    return this.snippetService.rawFileUrl(this.snippetId(), fileId);
  }

  async fork(): Promise<void> {
    try {
      const forked = await this.snippetService.fork(this.snippetId());
      this.toastService.success('Snippet forked');
      this.router.navigate(['/snippets', forked.id]);
    } catch {
      this.toastService.error('Failed to fork snippet');
    }
  }

  async deleteSnippet(): Promise<void> {
    const ok = await this.confirmModal.confirm(
      `Delete snippet "${this.snippet()?.title}"?`,
      'This will permanently delete the snippet and all its files. This cannot be undone.',
      { confirmLabel: 'Delete snippet', variant: 'danger' },
    );
    if (!ok) return;
    try {
      await this.snippetService.delete(this.snippetId());
      this.toastService.success('Snippet deleted');
      this.router.navigate(['/snippets']);
    } catch {
      this.toastService.error('Failed to delete snippet');
    }
  }

  async loadCommentPage(page: number): Promise<void> {
    this.loadingComments.set(true);
    try {
      const result = await this.snippetService.listComments(this.snippetId(), page, 10);
      this.comments.set(result.content);
      this.commentPage.set(result.number);
      this.commentTotalPages.set(result.totalPages);
      this.commentTotalElements.set(result.totalElements);
    } catch {
      this.toastService.error('Failed to load comments');
    } finally {
      this.loadingComments.set(false);
    }
  }

  async loadRevisionPage(page: number): Promise<void> {
    this.loadingRevisions.set(true);
    try {
      const result = await this.snippetService.listRevisions(this.snippetId(), page, 10);
      this.revisions.set(result.content);
      this.revisionPage.set(result.number);
      this.revisionTotalPages.set(result.totalPages);
    } catch {
      this.toastService.error('Failed to load revisions');
    } finally {
      this.loadingRevisions.set(false);
    }
  }

  onCommentInput(event: Event): void {
    this.commentBody.set((event.target as HTMLTextAreaElement).value);
  }

  async submitComment(): Promise<void> {
    if (!this.commentBody().trim()) return;
    this.submittingComment.set(true);
    try {
      const comment = await this.snippetService.addComment(this.snippetId(), this.commentBody());
      this.commentTotalElements.update((n) => n + 1);
      await this.loadCommentPage(this.commentPage());
      this.comments.update((c) => {
        const withNew = [...c];
        if (!withNew.find((x) => x.id === comment.id)) withNew.push(comment);
        return withNew;
      });
      this.commentBody.set('');
    } catch {
      this.toastService.error('Failed to post comment');
    } finally {
      this.submittingComment.set(false);
    }
  }

  async deleteComment(commentId: string): Promise<void> {
    const ok = await this.confirmModal.confirm('Delete comment?', undefined, {
      confirmLabel: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await this.snippetService.deleteComment(this.snippetId(), commentId);
      await this.loadCommentPage(this.commentPage());
    } catch {
      this.toastService.error('Failed to delete comment');
    }
  }

  canDeleteComment(comment: SnippetCommentInfo): boolean {
    return comment.author.username === this.currentUsername || this.isOwner();
  }
}
