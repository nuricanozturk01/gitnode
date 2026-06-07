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
import { copyTextToClipboard } from '../../shared/utils/clipboard.util';

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

  // Actions docs snippets
  readonly actionsWorkflowPath = '.originhub/workflows/ci.yaml';

  readonly actionsMinimalYaml = `name: CI

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: [self-hosted]
    steps:
      - uses: actions/checkout@v1
      - name: Run tests
        run: echo "Hello from OriginHub Actions"`;

  readonly actionsFullYaml = `name: Build and Test

on:
  push:
    branches: [main, develop]
    paths-ignore: ['**.md']
  pull_request:
    branches: [main]
  workflow_dispatch:
    inputs:
      environment:
        description: Deployment target
        type: string
        default: staging
        required: false

env:
  NODE_ENV: production

concurrency:
  group: ci-\${{ env.ORIGINHUB_REF }}
  cancel-in-progress: true

jobs:
  test:
    name: Test (\${{ matrix.node }})
    runs-on: [self-hosted, linux]
    timeout-minutes: 30
    strategy:
      matrix:
        node: ['20', '22']
      fail-fast: false
    steps:
      - uses: actions/checkout@v1
      - uses: actions/setup-node@v1
        with:
          node-version: \${{ matrix.node }}
      - name: Install dependencies
        run: npm ci
      - name: Run tests
        run: npm test
      - uses: actions/upload-artifact@v1
        with:
          name: test-results
          path: coverage/

  deploy:
    needs: [test]
    runs-on: [self-hosted]
    steps:
      - uses: actions/checkout@v1
      - name: Deploy
        env:
          API_KEY: \${{ secrets.DEPLOY_KEY }}
        run: ./scripts/deploy.sh \${{ inputs.environment }}`;

  readonly actionsDispatchYaml = `on:
  workflow_dispatch:
    inputs:
      environment:
        description: Target environment
        type: choice
        options:
          - staging
          - production
        default: staging
        required: true
      tag:
        description: Docker image tag (e.g. v1.2.3)
        type: string
        required: true
      dry_run:
        description: Skip actual deployment steps
        type: boolean
        default: false`;

  readonly actionsCacheYaml = `- uses: actions/cache@v1
  with:
    key: npm-\${{ hashFiles('package-lock.json') }}
    path: node_modules`;

  readonly actionsArtifactYaml = `# Upload
- uses: actions/upload-artifact@v1
  with:
    name: dist-bundle
    path: dist/

# Download in a later job
- uses: actions/download-artifact@v1
  with:
    name: dist-bundle
    path: dist/`;

  readonly actionsRunnerCmd = `./originhub-runner start \\
  --server-url http://localhost:8080 \\
  --token <registration-token> \\
  --name my-runner \\
  --labels self-hosted,linux \\
  --executor shell`;

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
    void copyTextToClipboard(text).then(() => this.toast.success('Copied to clipboard'));
    this.copiedId.set(id);
    if (this.copiedTimer !== null) clearTimeout(this.copiedTimer);
    this.copiedTimer = setTimeout(() => {
      this.copiedId.set(null);
      this.copiedTimer = null;
    }, 2000);
  }
}
