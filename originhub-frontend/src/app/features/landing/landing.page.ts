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

import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { environment } from '../../../environments/environment';
import { ToastService } from '../../core/toast/toast.service';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './landing.page.html',
  styleUrl: './landing.page.css',
})
export class LandingPage implements OnDestroy {
  private readonly toast = inject(ToastService);
  readonly copied = signal<'https' | 'ssh' | null>(null);
  private copiedTimer: ReturnType<typeof setTimeout> | null = null;

  /** Public source repository (releases & clone). */
  readonly githubRepoUrl = 'https://github.com/nuricanozturk01/originhub';

  /** Example HTTPS smart-Git clone (matches configured `apiUrl`). */
  readonly httpsCloneCommand = computed(() => {
    const base = environment.apiUrl.replace(/\/$/, '');
    return `git clone ${base}/git/owner/repo`;
  });

  /** Example SSH clone from configured `gitUrl`. */
  readonly sshCloneCommand = computed(() => `git clone ssh://${environment.gitUrl}/owner/repo.git`);

  ngOnDestroy(): void {
    if (this.copiedTimer !== null) {
      clearTimeout(this.copiedTimer);
      this.copiedTimer = null;
    }
  }

  copyCloneCommand(value: string, kind: 'https' | 'ssh'): void {
    void navigator.clipboard.writeText(value);
    this.toast.success('Copied to clipboard');
    this.copied.set(kind);
    if (this.copiedTimer !== null) clearTimeout(this.copiedTimer);
    this.copiedTimer = setTimeout(() => {
      this.copied.set(null);
      this.copiedTimer = null;
    }, 2000);
  }
}
