import { Injectable, computed, inject, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';

export const THEME_DARK = 'originhub' as const;
export const THEME_LIGHT = 'originhub-light' as const;
export type OriginHubTheme = typeof THEME_DARK | typeof THEME_LIGHT;

const STORAGE_KEY = 'originhub-admin-theme';

function isOriginHubTheme(value: string | null): value is OriginHubTheme {
  return value === THEME_DARK || value === THEME_LIGHT;
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  private readonly themeSignal = signal<OriginHubTheme>(this.readInitialTheme());

  readonly theme = this.themeSignal.asReadonly();
  readonly isDark = computed(() => this.themeSignal() === THEME_DARK);

  constructor() {
    this.applyTheme(this.themeSignal(), false);
  }

  setTheme(next: OriginHubTheme): void {
    this.applyTheme(next, true);
  }

  toggle(): void {
    this.setTheme(this.themeSignal() === THEME_DARK ? THEME_LIGHT : THEME_DARK);
  }

  private readInitialTheme(): OriginHubTheme {
    const fromDom = this.document.documentElement.getAttribute('data-theme');
    if (isOriginHubTheme(fromDom)) {
      return fromDom;
    }
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (isOriginHubTheme(stored)) {
        return stored;
      }
    } catch {
      /* storage unavailable */
    }
    return THEME_DARK;
  }

  private applyTheme(next: OriginHubTheme, persist: boolean): void {
    this.themeSignal.set(next);
    this.document.documentElement.setAttribute('data-theme', next);
    this.document.documentElement.style.colorScheme = next === THEME_LIGHT ? 'light' : 'dark';
    if (persist) {
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        /* private mode */
      }
    }
  }
}
