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

import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { SnippetService } from '../../../core/snippet/services/snippet.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { ToastService } from '../../../core/toast/toast.service';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import type { SnippetInfo, SnippetPage } from '../../../domain/snippet/models/snippet.model';

@Component({
  selector: 'app-snippets',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './snippets.page.html',
})
export class SnippetsPage implements OnInit {
  private readonly snippetService = inject(SnippetService);
  private readonly tokenService = inject(TokenService);
  private readonly toastService = inject(ToastService);

  readonly loading = signal(true);
  readonly activeTab = signal<'public' | 'mine'>('public');
  readonly publicPage = signal<SnippetPage | null>(null);
  readonly myPage = signal<SnippetPage | null>(null);
  readonly searchQuery = signal('');
  readonly searchInput = signal('');
  readonly currentPage = signal(0);
  readonly myCurrentPage = signal(0);

  readonly username = this.tokenService.getUsername() ?? '';

  ngOnInit(): void {
    this.loadPublic();
  }

  switchTab(tab: 'public' | 'mine'): void {
    this.activeTab.set(tab);
    if (tab === 'mine') {
      this.loadMine(0);
    } else {
      this.loadPublic();
    }
  }

  async loadPublic(page = 0): Promise<void> {
    this.loading.set(true);
    this.currentPage.set(page);
    try {
      const data = await this.snippetService.listPublic(page, 20, this.searchQuery() || undefined);
      this.publicPage.set(data);
    } catch {
      this.toastService.error('Failed to load snippets');
    } finally {
      this.loading.set(false);
    }
  }

  async loadMine(page = 0): Promise<void> {
    this.loading.set(true);
    this.myCurrentPage.set(page);
    try {
      const data = await this.snippetService.listMine(page);
      this.myPage.set(data);
    } catch {
      this.toastService.error('Failed to load your snippets');
    } finally {
      this.loading.set(false);
    }
  }

  onSearchInput(event: Event): void {
    this.searchInput.set((event.target as HTMLInputElement).value);
  }

  onSearchSubmit(): void {
    this.searchQuery.set(this.searchInput());
    this.loadPublic(0);
  }

  onSearchKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      this.onSearchSubmit();
    }
  }

  clearSearch(): void {
    this.searchInput.set('');
    this.searchQuery.set('');
    this.loadPublic(0);
  }

  nextPage(): void {
    const p = this.publicPage();
    if (p && this.currentPage() < p.totalPages - 1) {
      this.loadPublic(this.currentPage() + 1);
    }
  }

  prevPage(): void {
    if (this.currentPage() > 0) {
      this.loadPublic(this.currentPage() - 1);
    }
  }

  nextMinePage(): void {
    const p = this.myPage();
    if (p && this.myCurrentPage() < p.totalPages - 1) {
      this.loadMine(this.myCurrentPage() + 1);
    }
  }

  prevMinePage(): void {
    if (this.myCurrentPage() > 0) {
      this.loadMine(this.myCurrentPage() - 1);
    }
  }

  snippetList(): SnippetInfo[] {
    if (this.activeTab() === 'mine') {
      return this.myPage()?.content ?? [];
    }
    return this.publicPage()?.content ?? [];
  }
}
