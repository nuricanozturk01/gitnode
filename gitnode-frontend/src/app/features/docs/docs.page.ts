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

type DocsTab = 'git' | 'actions' | 'features';

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

  readonly activeTab = signal<DocsTab>('git');

  private readonly sectionTabMap: Record<string, DocsTab> = {
    'local-setup': 'git',
    'http-git': 'git',
    'ssh-config': 'git',
    'clone-push': 'git',
    'actions-overview': 'actions',
    'actions-quickstart': 'actions',
    'actions-yaml': 'actions',
    'actions-dispatch': 'actions',
    'actions-builtin': 'actions',
    'actions-runner': 'actions',
    collaborators: 'features',
    'gh-migration': 'features',
    issues: 'features',
    kanban: 'features',
    snippets: 'features',
    webhooks: 'features',
    releases: 'features',
    forks: 'features',
    'admin-panel': 'features',
    'saml-ldap': 'features',
  };

  readonly httpsCloneLine = computed(() => {
    const base = environment.apiUrl.replace(/\/$/, '');
    return `git clone ${base}/git/owner/repo`;
  });

  readonly sshCloneLine = computed(() => {
    const host = environment.gitUrl.replace(/\/$/, '');
    return `git clone ssh://${host}/owner/repo.git`;
  });

  readonly sshKeygenCmd = 'ssh-keygen -t ed25519 -C "your-email@example.com"';
  readonly catPubCmd = 'cat ~/.ssh/id_ed25519.pub';
  readonly sshConfigBlock = `Host gitnode-local
    HostName localhost
    Port 2222
    User git
    IdentityFile ~/.ssh/id_ed25519`;
  readonly sshAliasFlow = `git clone gitnode-local:owner/repo.git
cd repo
git push origin main`;

  // Actions snippets
  readonly actionsWorkflowPath = '.gitnode/workflows/ci.yaml';

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
        run: echo "Hello from GitNode Actions"`;

  readonly actionsFullYaml = `name: Build and Test

on:
  push:
    branches: [main, develop]
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
  group: build-\${{ inputs.environment }}
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
    key: npm-deps-v1
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

  readonly actionsRunnerCmd = `./gitnode-runner start \\
  --server-url http://localhost:8080 \\
  --token <registration-token> \\
  --name my-runner \\
  --labels self-hosted,linux \\
  --executor shell`;

  readonly actionsSecretsYaml = `jobs:
  deploy:
    runs-on: [self-hosted]
    steps:
      - uses: actions/checkout@v1
      - name: Push Docker image
        env:
          REGISTRY_PASSWORD: \${{ secrets.REGISTRY_PASSWORD }}
          DATABASE_URL: \${{ secrets.DATABASE_URL }}
        run: |
          echo "$REGISTRY_PASSWORD" | docker login -u myuser --password-stdin
          ./deploy.sh`;

  readonly webhookHeadersExample = `POST https://your-server.example.com/hook
Content-Type: application/json
X-Hub-Signature-256: sha256=a1b2c3d4e5f6...

{
  "event": "repo.pushed",
  "timestamp": "2026-06-08T10:30:00Z",
  "repoId": "uuid...",
  "data": {
    "branchName": "main",
    "pusher": "alice"
  }
}`;

  readonly webhookVerifyPython = `import hmac, hashlib

secret = b"your-webhook-secret"
payload = request.body          # raw bytes

sig = request.headers.get("X-Hub-Signature-256", "")
expected = "sha256=" + hmac.new(secret, payload, hashlib.sha256).hexdigest()

if not hmac.compare_digest(sig, expected):
    abort(401)`;

  readonly samlKeygenCmd = 'make saml-keygen   # writes key + cert to ~/.gitnode/saml/';

  setTab(tab: DocsTab): void {
    this.activeTab.set(tab);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  scrollToSection(sectionId: string): void {
    const tab = this.sectionTabMap[sectionId];
    if (tab && tab !== this.activeTab()) {
      this.activeTab.set(tab);
      setTimeout(() => {
        document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 50);
    } else {
      document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  ngOnDestroy(): void {
    if (this.copiedTimer !== null) {
      clearTimeout(this.copiedTimer);
      this.copiedTimer = null;
    }
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
