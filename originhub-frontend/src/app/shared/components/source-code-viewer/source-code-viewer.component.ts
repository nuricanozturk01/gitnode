import {
  ChangeDetectionStrategy,
  Component,
  ViewEncapsulation,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { LucideAngularModule } from 'lucide-angular';
import { ThemeService } from '../../../core/theme/theme.service';
import type { HLJSApi } from 'highlight.js';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-source-code-viewer',
  standalone: true,
  imports: [LucideAngularModule],
  encapsulation: ViewEncapsulation.None,
  templateUrl: './source-code-viewer.component.html',
  styleUrl: './source-code-viewer.component.css',
})
export class SourceCodeViewerComponent {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly appTheme = inject(ThemeService);

  readonly content = input.required<string>();
  readonly language = input<string>('yaml');
  readonly fileName = input<string>('');

  readonly syntaxTheme = computed(() => (this.appTheme.isDark() ? 'dark' : 'light'));
  readonly highlightedLines = signal<SafeHtml[]>([]);
  readonly copied = signal(false);

  private hljsModule: Promise<HLJSApi> | null = null;
  private highlightGeneration = 0;
  private copiedTimer: ReturnType<typeof setTimeout> | null = null;

  readonly lineCount = computed(() => this.content().split('\n').length);

  constructor() {
    effect(() => {
      const text = this.content();
      const lang = this.language();
      void this.applyHighlight(text, lang);
    });
  }

  async copyContent(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.content());
      this.copied.set(true);
      if (this.copiedTimer) clearTimeout(this.copiedTimer);
      this.copiedTimer = setTimeout(() => this.copied.set(false), 2000);
    } catch {
      // ignore
    }
  }

  private loadHljs(): Promise<HLJSApi> {
    if (!this.hljsModule) {
      this.hljsModule = import('highlight.js').then((m) => m.default);
    }
    return this.hljsModule;
  }

  private async applyHighlight(text: string, language: string): Promise<void> {
    const generation = ++this.highlightGeneration;
    const hljs = await this.loadHljs();
    if (generation !== this.highlightGeneration) return;

    let highlighted: string;
    if (language && hljs.getLanguage(language)) {
      highlighted = hljs.highlight(text, { language, ignoreIllegals: true }).value;
    } else {
      highlighted = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    if (generation !== this.highlightGeneration) return;

    const lines = highlighted.split('\n').map((line) => this.sanitizer.bypassSecurityTrustHtml(line || '&nbsp;'));
    this.highlightedLines.set(lines);
  }
}
