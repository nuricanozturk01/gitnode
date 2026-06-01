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
import { SnippetService } from '../../../core/snippet/services/snippet.service';
import { ToastService } from '../../../core/toast/toast.service';
import { ThemeService } from '../../../core/theme/theme.service';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { filenameToHljsLanguage, isMarkdown } from '../shared/language-detect.util';
import { marked } from 'marked';
import type { SnippetRevisionDetail, SnippetFileInfo } from '../../../domain/snippet/models/snippet.model';

interface HighlightedFile {
  file: SnippetFileInfo;
  language: string;
  lines: SafeHtml[];
  isMarkdown: boolean;
  markdownHtml: SafeHtml | null;
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-snippet-revision',
  standalone: true,
  encapsulation: ViewEncapsulation.None,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './snippet-revision.page.html',
  styleUrl: './snippet-revision.page.css',
})
export class SnippetRevisionPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snippetService = inject(SnippetService);
  private readonly toastService = inject(ToastService);
  private readonly sanitizer = inject(DomSanitizer);
  readonly theme = inject(ThemeService);

  readonly loading = signal(true);
  readonly revision = signal<SnippetRevisionDetail | null>(null);
  readonly highlightedFiles = signal<HighlightedFile[]>([]);
  readonly snippetId = signal('');

  readonly blobSyntaxTheme = computed(() => (this.theme.isDark() ? 'dark' : 'light'));

  ngOnInit(): void {
    const snippetId = this.route.snapshot.paramMap.get('snippetId') ?? '';
    const revisionId = this.route.snapshot.paramMap.get('revisionId') ?? '';
    this.snippetId.set(snippetId);
    this.load(snippetId, revisionId);
  }

  private async load(snippetId: string, revisionId: string): Promise<void> {
    this.loading.set(true);
    try {
      const detail = await this.snippetService.getRevision(snippetId, revisionId);
      this.revision.set(detail);
      await this.buildHighlights(detail);
    } catch {
      this.toastService.error('Revision not found');
      this.router.navigate(['/snippets', snippetId]);
    } finally {
      this.loading.set(false);
    }
  }

  private async buildHighlights(detail: SnippetRevisionDetail): Promise<void> {
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
        });
      }
    }

    this.highlightedFiles.set(result);
  }

  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text).then(() => {
      this.toastService.success('Copied to clipboard');
    });
  }
}
