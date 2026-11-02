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

import { Injectable, computed, inject, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';

export const THEME_DARK = 'gitnode' as const;
export const THEME_LIGHT = 'gitnode-light' as const;
export type GitNodeTheme = typeof THEME_DARK | typeof THEME_LIGHT;
export type ThemePreference = GitNodeTheme | 'system';

const STORAGE_KEY = 'gitnode-theme';

function isThemePreference(value: string | null): value is ThemePreference {
  return value === THEME_DARK || value === THEME_LIGHT || value === 'system';
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly media = this.document.defaultView?.matchMedia('(prefers-color-scheme: dark)');
  private readonly preferenceSignal = signal<ThemePreference>(this.readInitialPreference());
  private readonly themeSignal = signal<GitNodeTheme>(this.resolveTheme(this.preferenceSignal()));

  readonly preference = this.preferenceSignal.asReadonly();
  readonly theme = this.themeSignal.asReadonly();
  readonly isDark = computed(() => this.themeSignal() === THEME_DARK);

  constructor() {
    this.applyTheme();
    this.media?.addEventListener('change', () => {
      if (this.preferenceSignal() === 'system') this.applyTheme();
    });
    this.document.defaultView?.addEventListener('storage', (event) => {
      if (event.key === STORAGE_KEY && (isThemePreference(event.newValue) || event.newValue === null)) {
        this.preferenceSignal.set(isThemePreference(event.newValue) ? event.newValue : 'system');
        this.applyTheme();
      }
    });
  }

  setPreferenceFromEvent(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    if (!isThemePreference(value)) return;
    this.preferenceSignal.set(value);
    this.applyTheme();
    try {
      localStorage.setItem(STORAGE_KEY, value);
    } catch {
      /* storage unavailable */
    }
  }

  private readInitialPreference(): ThemePreference {
    try {
      const stored = localStorage.getItem(STORAGE_KEY) ?? localStorage.getItem('gitnode-admin-theme');
      return isThemePreference(stored) ? stored : 'system';
    } catch {
      return 'system';
    }
  }

  private resolveTheme(preference: ThemePreference): GitNodeTheme {
    return preference === 'system' ? (this.media?.matches ? THEME_DARK : THEME_LIGHT) : preference;
  }

  private applyTheme(): void {
    const next = this.resolveTheme(this.preferenceSignal());
    this.themeSignal.set(next);
    this.document.documentElement.setAttribute('data-theme', next);
    this.document.documentElement.style.colorScheme = next === THEME_LIGHT ? 'light' : 'dark';
  }
}
