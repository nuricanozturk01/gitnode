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

import { Pipe, PipeTransform, inject } from '@angular/core';
import { marked } from 'marked';
import { DomSanitizer, type SafeHtml } from '@angular/platform-browser';

marked.use({ gfm: true, breaks: false });

const CACHE_MAX = 48;

@Pipe({ name: 'markdown', standalone: true })
export class MarkdownPipe implements PipeTransform {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly cache = new Map<string, SafeHtml>();

  transform(value: string | null | undefined): SafeHtml {
    if (!value) return '';
    const cached = this.cache.get(value);
    if (cached) return cached;

    const html = marked.parse(value, { async: false }) as string;
    const safe = this.sanitizer.bypassSecurityTrustHtml(html);
    if (this.cache.size >= CACHE_MAX) {
      const oldest = this.cache.keys().next().value;
      if (oldest !== undefined) this.cache.delete(oldest);
    }
    this.cache.set(value, safe);
    return safe;
  }
}
