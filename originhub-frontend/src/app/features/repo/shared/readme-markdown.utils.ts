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

import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

/** Parent path of a repo file (e.g. `docs/README.md` → `docs`). */
export function readmeParentDir(readmePath: string): string {
  const i = readmePath.lastIndexOf('/');
  return i === -1 ? '' : readmePath.slice(0, i);
}

function stripQueryAndHash(path: string): string {
  let s = path;
  const hash = s.indexOf('#');
  if (hash !== -1) s = s.slice(0, hash);
  const q = s.indexOf('?');
  if (q !== -1) s = s.slice(0, q);
  return s;
}

/**
 * Resolve a repo-relative path from the README file directory (GitHub-style).
 * Leading `/` is treated as repository root.
 */
export function resolveRepoRelativePath(readmeDir: string, spec: string): string {
  let rel = spec.trim();
  rel = rel.replace(/^\.\//, '');
  const fromRoot = rel.startsWith('/');
  if (fromRoot) {
    rel = rel.slice(1);
  }
  const pathOnly = stripQueryAndHash(rel);
  const baseParts = fromRoot ? [] : readmeDir.split('/').filter(Boolean);
  const relParts = pathOnly.split('/').filter((p) => p && p !== '.');
  const stack = [...baseParts];
  for (const p of relParts) {
    if (p === '..') {
      if (stack.length > 0) stack.pop();
    } else {
      stack.push(p);
    }
  }
  return stack.join('/');
}

export function isExternalOrSpecialUrl(spec: string): boolean {
  const s = spec.trim();
  if (!s || s.startsWith('#')) return true;
  if (/^[a-z][a-z0-9+.-]*:/i.test(s)) return true;
  if (s.startsWith('//')) return true;
  return false;
}

export function buildRawFileUrl(
  apiBase: string,
  owner: string,
  repo: string,
  branch: string,
  repoRelativePath: string,
): string {
  const root = apiBase.replace(/\/$/, '');
  const segments = repoRelativePath
    .split('/')
    .filter(Boolean)
    .map((p) => encodeURIComponent(p));
  return `${root}/api/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/raw/${encodeURIComponent(branch)}/${segments.join('/')}`;
}

/** SPA path: `/:owner/:repo/blob/:branch/...segments` */
export function buildBlobViewerPath(owner: string, repo: string, branch: string, repoRelativePath: string): string {
  const segments = repoRelativePath
    .split('/')
    .filter(Boolean)
    .map((p) => encodeURIComponent(p));
  return `/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/blob/${encodeURIComponent(branch)}/${segments.join('/')}`;
}

export interface ReadmePostProcessOptions {
  readmePath: string;
  owner: string;
  repo: string;
  branch: string;
  apiUrl: string;
  http: HttpClient;
  registerObjectUrl: (url: string) => void;
}

/**
 * Rewrites relative `img` and `a` targets to authenticated raw fetches (images) and in-app blob routes (links).
 */
export async function postProcessReadmeHtml(html: string, opts: ReadmePostProcessOptions): Promise<string> {
  const parser = new DOMParser();
  const doc = parser.parseFromString(html, 'text/html');
  const readmeDir = readmeParentDir(opts.readmePath);

  for (const a of doc.querySelectorAll('a[href]')) {
    const href = a.getAttribute('href');
    if (!href) continue;
    if (href.startsWith('#')) continue;
    const hashIdx = href.indexOf('#');
    const pathPart = hashIdx === -1 ? href : href.slice(0, hashIdx);
    const fragment = hashIdx === -1 ? '' : href.slice(hashIdx);
    if (isExternalOrSpecialUrl(pathPart)) continue;
    const resolved = resolveRepoRelativePath(readmeDir, pathPart);
    if (!resolved) continue;
    a.setAttribute('href', buildBlobViewerPath(opts.owner, opts.repo, opts.branch, resolved) + fragment);
  }

  const imageLoads: Promise<void>[] = [];
  for (const img of doc.querySelectorAll('img[src]')) {
    const src = img.getAttribute('src');
    if (!src || isExternalOrSpecialUrl(src)) continue;
    const resolved = resolveRepoRelativePath(readmeDir, src);
    if (!resolved) continue;
    const rawUrl = buildRawFileUrl(opts.apiUrl, opts.owner, opts.repo, opts.branch, resolved);
    imageLoads.push(
      (async () => {
        try {
          const blob = await firstValueFrom(opts.http.get(rawUrl, { responseType: 'blob' }));
          const objectUrl = URL.createObjectURL(blob);
          opts.registerObjectUrl(objectUrl);
          img.setAttribute('src', objectUrl);
        } catch {
          img.setAttribute('src', '');
          if (!img.getAttribute('alt')) {
            img.setAttribute('alt', resolved);
          }
        }
      })(),
    );
  }

  await Promise.all(imageLoads);

  return doc.body.innerHTML;
}
