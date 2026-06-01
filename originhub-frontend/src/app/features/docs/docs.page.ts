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

import { Component, ChangeDetectionStrategy, OnDestroy, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { environment } from '../../../environments/environment';
import { ToastService } from '../../core/toast/toast.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-docs',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './docs.page.html',
  styleUrl: './docs.page.css',
})
export class DocsPage implements OnDestroy {
  private readonly toast = inject(ToastService);
  readonly copiedId = signal<string | null>(null);
  private copiedTimer: ReturnType<typeof setTimeout> | null = null;

  readonly httpsCloneLine = computed(() => {
    const base = environment.apiUrl.replace(/\/$/, '');
    return `git clone ${base}/git/owner/repo`;
  });

  readonly sshCloneLine = computed(() => `git clone ssh://${environment.gitUrl}/owner/repo.git`);

  readonly sshKeygenCmd = 'ssh-keygen -t ed25519 -C "your-email@example.com"';
  readonly catPubCmd = 'cat ~/.ssh/id_ed25519.pub';
  readonly sshConfigBlock = `Host originhub-local
    HostName localhost
    Port 2222
    User git
    IdentityFile ~/.ssh/id_ed25519`;
  readonly sshAliasFlow = `git clone originhub-local:username/repo-name.git
cd repo-name
git push origin main`;

  ngOnDestroy(): void {
    if (this.copiedTimer !== null) {
      clearTimeout(this.copiedTimer);
      this.copiedTimer = null;
    }
  }

  scrollToSection(sectionId: string): void {
    const el = document.getElementById(sectionId);
    el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  copySnippet(id: string, text: string): void {
    void navigator.clipboard.writeText(text);
    this.toast.success('Copied to clipboard');
    this.copiedId.set(id);
    if (this.copiedTimer !== null) clearTimeout(this.copiedTimer);
    this.copiedTimer = setTimeout(() => {
      this.copiedId.set(null);
      this.copiedTimer = null;
    }, 2000);
  }
}
