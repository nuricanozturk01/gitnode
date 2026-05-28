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

import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { RepoService } from '../../../core/repo/services/repo.service';
import { ToastService } from '../../../core/toast/toast.service';

@Component({
  selector: 'app-create-repo',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule],
  templateUrl: './create-repo.page.html',
  styleUrl: './create-repo.page.css',
})
export class CreateRepoPage {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly repoService = inject(RepoService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly topicInput = signal('');

  private static readonly MAX_TOPICS = 6;

  readonly form = this.fb.nonNullable.group({
    name: [
      '',
      [
        Validators.required,
        Validators.minLength(1),
        Validators.maxLength(100),
        Validators.pattern(/^[a-zA-Z0-9_.-]+$/),
      ],
    ],
    description: ['', [Validators.maxLength(500)]],
    topics: [[] as string[]],
    isPrivate: [true],
  });

  addTopic(): void {
    const value = this.topicInput().trim().toLowerCase();
    if (!value) return;
    const topics = this.form.getRawValue().topics;
    if (topics.length >= CreateRepoPage.MAX_TOPICS) return;
    if (!/^[a-zA-Z0-9-]+$/.test(value)) return;
    if (topics.includes(value)) return;
    this.form.patchValue({ topics: [...topics, value] });
    this.topicInput.set('');
  }

  removeTopic(topic: string): void {
    const topics = this.form.getRawValue().topics.filter((t) => t !== topic);
    this.form.patchValue({ topics });
  }

  onTopicKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.addTopic();
    }
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const raw = this.form.getRawValue();
      const repo = await this.repoService.create({
        name: raw.name,
        description: raw.description || undefined,
        topics: raw.topics.length > 0 ? raw.topics : undefined,
        isPrivate: raw.isPrivate,
      });
      this.toast.success('Repository created successfully');
      this.router.navigate(['/', repo.owner.username, repo.name]);
    } catch (err) {
      const msg =
        err instanceof HttpErrorResponse
          ? (err.error?.message ?? err.error?.error ?? err.message)
          : err instanceof Error
            ? err.message
            : 'Failed to create repository';
      this.error.set(msg);
      this.toast.error(msg);
    } finally {
      this.loading.set(false);
    }
  }
}
