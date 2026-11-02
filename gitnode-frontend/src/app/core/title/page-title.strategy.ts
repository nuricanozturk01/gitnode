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

import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

const APP_NAME = 'GitNode';

const STATIC_TITLES: Record<string, string> = {
  '': 'Dashboard',
  login: 'Sign in',
  register: 'Create account',
  settings: 'Settings',
  notifications: 'Notifications',
  snippets: 'Snippets',
  projects: 'Projects',
  new: 'New repository',
  docs: 'Documentation',
  migrate: 'Import repository',
};

const REPO_SECTIONS: Record<string, string> = {
  issues: 'Issues',
  pulls: 'Pull requests',
  branches: 'Branches',
  commits: 'Commits',
  commit: 'Commit',
  releases: 'Releases',
  settings: 'Settings',
  editor: 'Editor',
  blob: 'File',
  tree: 'Files',
  actions: 'Actions',
  tags: 'Tags',
  compare: 'Compare',
};

/**
 * Gives every route a document title so screen-reader users hear where they are after an in-app
 * navigation. Routes can still set an explicit `title`; otherwise it is derived from the URL.
 */
@Injectable({ providedIn: 'root' })
export class PageTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const explicit = this.buildTitle(snapshot);
    const label = explicit ?? this.derive(snapshot.url);
    this.title.setTitle(label ? `${label} · ${APP_NAME}` : APP_NAME);
  }

  private derive(url: string): string {
    const path = url.split(/[?#]/)[0];
    const segments = path.split('/').filter(Boolean).map(decodeURIComponent);
    if (segments.length === 0) return STATIC_TITLES[''];
    const first = segments[0];
    if (segments.length === 1) return STATIC_TITLES[first] ?? `${first}`;
    if (first === 'projects') return 'Projects';
    if (STATIC_TITLES[first] && first !== 'new') return STATIC_TITLES[first];
    const repo = `${segments[0]}/${segments[1]}`;
    const section = segments[2] ? REPO_SECTIONS[segments[2]] : undefined;
    return section ? `${section} · ${repo}` : repo;
  }
}
