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

import { Component, ChangeDetectionStrategy, inject, signal, computed, effect, OnDestroy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { MigrationService } from '../../core/migration/migration.service';
import { ToastService } from '../../core/toast/toast.service';
import { UserService } from '../../core/user/services/user.service';
import { TokenService } from '../../core/auth/services/token.service';
import type { User } from '../../domain/auth/models/user.model';
import type { MigrationItemKind } from '../../domain/migration/models/migration.models';

export interface ParsedGithubRepo {
  normalizedUrl: string;
  owner: string;
  repo: string;
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-migrate-github',
  standalone: true,
  imports: [ReactiveFormsModule, LucideAngularModule, AvatarComponent],
  templateUrl: './migrate-github.page.html',
  styleUrl: './migrate-github.page.css',
})
export class MigrateGithubPage implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private pollInterval: ReturnType<typeof setInterval> | null = null;
  private readonly router = inject(Router);
  private readonly migrationService = inject(MigrationService);
  private readonly toast = inject(ToastService);
  private readonly userService = inject(UserService);
  private readonly tokenService = inject(TokenService);

  readonly user = signal<User | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly parsed = signal<ParsedGithubRepo | null>(null);

  readonly ownerMatchesMe = computed(() => {
    const u = this.user();
    const p = this.parsed();
    if (!u || !p) return false;
    return u.username.toLowerCase() === p.owner.toLowerCase();
  });

  readonly form = this.fb.nonNullable.group({
    cloneUrl: ['', [Validators.required]],
    accessToken: ['', [Validators.required]],
    migrateRepository: [true],
    migratePullRequests: [false],
    migrateTagsAndReleases: [false],
    repoNameOverride: [''],
  });

  constructor() {
    effect(() => {
      if (!this.tokenService.isLoggedIn()) {
        this.user.set(null);
        return;
      }
      void this.loadUser();
    });
  }

  ngOnDestroy(): void {
    this.clearPollInterval();
  }

  private async loadUser(): Promise<void> {
    try {
      const u = await this.userService.getMe();
      this.user.set(u);
    } catch {
      const username = this.tokenService.getUsername();
      if (username) {
        this.user.set({
          id: '',
          username,
          email: '',
          displayName: username,
          avatarUrl: null,
          bio: null,
          website: null,
          location: null,
          profileReadme: null,
          createdAt: '',
          updatedAt: '',
        });
      }
    }
  }

  onCloneUrlBlur(): void {
    const raw = this.form.getRawValue().cloneUrl.trim();
    this.applyParsed(MigrateGithubPage.parseGithubUrl(raw));
  }

  onRepoNameBlur(): void {
    const p = this.parsed();
    if (!p) return;
    const name = this.form.getRawValue().repoNameOverride.trim();
    if (!name || !/^[a-zA-Z0-9_.-]+$/.test(name)) {
      this.form.patchValue({ repoNameOverride: p.repo }, { emitEvent: false });
      return;
    }
    if (name === p.repo) return;
    const nextUrl = `https://github.com/${p.owner}/${name}`;
    const next = MigrateGithubPage.parseGithubUrl(nextUrl);
    if (next) {
      this.form.patchValue({ cloneUrl: next.normalizedUrl, repoNameOverride: next.repo }, { emitEvent: false });
      this.applyParsed(next);
    }
  }

  private applyParsed(next: ParsedGithubRepo | null): void {
    this.parsed.set(next);
    if (next) {
      this.form.patchValue({ repoNameOverride: next.repo }, { emitEvent: false });
    } else {
      this.form.patchValue({ repoNameOverride: '' }, { emitEvent: false });
    }
  }

  /** Backend validates the same shape as `MigrationForm.url`. */
  static parseGithubUrl(input: string): ParsedGithubRepo | null {
    const trimmed = input.trim();
    if (!trimmed) return null;
    const noTrail = trimmed.replace(/\/+$/, '').replace(/\.git$/i, '');
    const re = /^(?:https?:\/\/)?(?:www\.)?github\.com\/([\w.-]+)\/([\w.-]+)$/i;
    const m = noTrail.match(re);
    if (!m) return null;
    const owner = m[1];
    const repo = m[2];
    return {
      normalizedUrl: `https://github.com/${owner}/${repo}`,
      owner,
      repo,
    };
  }

  static primaryAccessToken(raw: string): string {
    for (const part of raw.split(',')) {
      const t = part.trim();
      if (t.length > 0) return t;
    }
    return '';
  }

  private buildMigrationItems(): MigrationItemKind[] {
    const v = this.form.getRawValue();
    const items: MigrationItemKind[] = [];
    if (v.migrateRepository) items.push('REPOSITORIES');
    if (v.migratePullRequests) items.push('PULL_REQUESTS');
    if (v.migrateTagsAndReleases) items.push('TAGS_AND_RELEASES');
    return items;
  }

  async onSubmit(): Promise<void> {
    this.error.set(null);
    this.onCloneUrlBlur();
    let p = this.parsed();
    const repoOverride = this.form.getRawValue().repoNameOverride.trim();
    if (p && repoOverride && /^[a-zA-Z0-9_.-]+$/.test(repoOverride)) {
      const next = MigrateGithubPage.parseGithubUrl(`https://github.com/${p.owner}/${repoOverride}`);
      if (next) {
        this.applyParsed(next);
        this.form.patchValue({ cloneUrl: next.normalizedUrl }, { emitEvent: false });
        p = next;
      }
    }
    if (!p) {
      this.error.set('Enter a valid GitHub repository URL (for example https://github.com/octocat/Hello-World).');
      return;
    }

    const items = this.buildMigrationItems();
    if (items.length === 0) {
      this.error.set('Select at least one migration item.');
      return;
    }

    const token = MigrateGithubPage.primaryAccessToken(this.form.getRawValue().accessToken);
    if (!token) {
      this.error.set('Access token is required.');
      return;
    }

    this.loading.set(true);
    try {
      const res = await this.migrationService.create({
        service: 'GITHUB',
        url: p.normalizedUrl,
        accessToken: token,
        migrationItems: items,
        owner: p.owner,
        repoName: p.repo,
      });

      this.toast.success('Migration started. You will be notified when it completes.');
      await this.router.navigate(['/dashboard']);

      this.startPolling(res.jobId);
    } catch (err) {
      const msg =
        err instanceof HttpErrorResponse
          ? (err.error?.message ?? err.error?.error ?? err.message)
          : err instanceof Error
            ? err.message
            : 'Migration request failed';
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.loading.set(false);
    }
  }

  private startPolling(jobId: string): void {
    this.clearPollInterval();
    const maxAttempts = 16; // 16 * 10s ≈ 2.5 min
    let attempts = 0;

    this.pollInterval = setInterval(async () => {
      attempts++;
      try {
        const job = await this.migrationService.getJob(jobId);

        if (job.status === 'COMPLETED') {
          this.clearPollInterval();
          this.toast.success('Git Repo Migration completed successfully!', 45_000);
          return;
        }

        if (job.status === 'FAILED') {
          this.clearPollInterval();
          this.toast.error(job.errorMessage?.trim() || 'Migration failed');
          return;
        }
      } catch {
        this.clearPollInterval();
      }

      if (attempts >= maxAttempts) {
        this.clearPollInterval();
      }
    }, 10_000);
  }

  private clearPollInterval(): void {
    if (this.pollInterval !== null) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }
}
