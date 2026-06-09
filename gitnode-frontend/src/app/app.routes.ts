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

import { Routes } from '@angular/router';
import { authGuard } from './core/auth/guards/auth.guard';
import { repoOwnerGuard } from './core/repo/guards/repo-owner.guard';
import { repoSettingsGuard } from './core/repo/guards/repo-settings.guard';
import { guestGuard } from './core/auth/guards/guest.guard';
import { redirectIfAuthGuard } from './core/auth/guards/redirect-if-auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/landing/landing.page').then((m) => m.LandingPage),
    canActivate: [redirectIfAuthGuard],
  },
  {
    path: 'docs',
    loadComponent: () => import('./features/docs/docs.page').then((m) => m.DocsPage),
  },

  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.page').then((m) => m.LoginPage),
    canActivate: [guestGuard],
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register.page').then((m) => m.RegisterPage),
    canActivate: [guestGuard],
  },

  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
    canActivate: [authGuard],
  },
  {
    path: 'new',
    loadComponent: () => import('./features/repo/create/create-repo.page').then((m) => m.CreateRepoPage),
    canActivate: [authGuard],
  },
  {
    path: 'migrate',
    loadComponent: () => import('./features/migration/migrate-github.page').then((m) => m.MigrateGithubPage),
    canActivate: [authGuard],
  },
  {
    path: 'settings',
    loadComponent: () => import('./features/user-settings/user-settings.page').then((m) => m.UserSettingsPage),
    canActivate: [authGuard],
  },

  {
    path: 'projects',
    loadComponent: () => import('./features/project/projects/projects.page').then((m) => m.ProjectsPage),
    canActivate: [authGuard],
  },
  {
    path: 'projects/:owner/:projectCode/settings',
    loadComponent: () =>
      import('./features/project/project-settings/project-settings.page').then((m) => m.ProjectSettingsPage),
    canActivate: [authGuard],
  },
  {
    path: 'projects/:owner/:projectCode/tasks/:taskCode',
    loadComponent: () => import('./features/project/task-detail/task-detail.page').then((m) => m.TaskDetailPage),
  },
  {
    path: 'projects/:owner/:projectCode',
    loadComponent: () => import('./features/project/board/board.page').then((m) => m.BoardPage),
  },

  {
    path: 'accept-invite/:token',
    loadComponent: () => import('./features/collaborator/accept-invite.page').then((m) => m.AcceptInvitePage),
  },
  {
    path: 'snippets',
    loadComponent: () => import('./features/snippets/snippets/snippets.page').then((m) => m.SnippetsPage),
    canActivate: [authGuard],
  },
  {
    path: 'snippets/new',
    loadComponent: () => import('./features/snippets/snippet-edit/snippet-edit.page').then((m) => m.SnippetEditPage),
    canActivate: [authGuard],
  },
  {
    path: 'snippets/:snippetId/edit',
    loadComponent: () => import('./features/snippets/snippet-edit/snippet-edit.page').then((m) => m.SnippetEditPage),
    canActivate: [authGuard],
  },
  {
    path: 'snippets/:snippetId/revisions/:revisionId',
    loadComponent: () =>
      import('./features/snippets/snippet-revision/snippet-revision.page').then((m) => m.SnippetRevisionPage),
  },
  {
    path: 'snippets/:snippetId',
    loadComponent: () =>
      import('./features/snippets/snippet-detail/snippet-detail.page').then((m) => m.SnippetDetailPage),
  },

  {
    path: ':username',
    loadComponent: () => import('./features/user-profile/user-profile.page').then((m) => m.UserProfilePage),
  },

  {
    path: ':owner/:repo',
    loadComponent: () => import('./features/repo/layout/repo-layout.component').then((m) => m.RepoLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/repo/home/repo-home.page').then((m) => m.RepoHomePage),
      },
      {
        path: 'branches',
        loadComponent: () => import('./features/repo/branches/branches.page').then((m) => m.BranchesPage),
      },
      {
        path: 'commits/:branch',
        loadComponent: () => import('./features/repo/commits/commits.page').then((m) => m.CommitsPage),
      },
      {
        path: 'commit/:sha',
        loadComponent: () => import('./features/repo/commits/commit-detail.page').then((m) => m.CommitDetailPage),
      },
      {
        path: 'tree',
        children: [
          {
            path: '**',
            loadComponent: () => import('./features/repo/tree/tree.page').then((m) => m.TreePage),
          },
        ],
      },
      {
        path: 'blob',
        children: [
          {
            path: '**',
            loadComponent: () => import('./features/repo/blob/blob.page').then((m) => m.BlobPage),
          },
        ],
      },
      {
        path: 'issues',
        loadComponent: () => import('./features/repo/issues/issues.page').then((m) => m.IssuesPage),
      },
      {
        path: 'issues/new',
        loadComponent: () => import('./features/repo/issues/new-issue.page').then((m) => m.NewIssuePage),
        canActivate: [authGuard],
      },
      {
        path: 'issues/:number',
        loadComponent: () => import('./features/repo/issues/issue-detail.page').then((m) => m.IssueDetailPage),
      },
      {
        path: 'pulls',
        loadComponent: () => import('./features/repo/pull-requests/pull-requests.page').then((m) => m.PullRequestsPage),
      },
      {
        path: 'pulls/new',
        loadComponent: () =>
          import('./features/repo/pull-requests/new-pull-request.page').then((m) => m.NewPullRequestPage),
        canActivate: [repoOwnerGuard],
      },
      {
        path: 'pulls/:number',
        loadComponent: () => import('./features/repo/pull-requests/pr-detail.page').then((m) => m.PrDetailPage),
      },
      {
        path: 'snippets',
        loadComponent: () => import('./features/repo/snippets/repo-snippets.page').then((m) => m.RepoSnippetsPage),
      },
      {
        path: 'projects',
        loadComponent: () => import('./features/repo/projects/repo-projects.page').then((m) => m.RepoProjectsPage),
      },
      {
        path: 'releases',
        loadComponent: () => import('./features/repo/releases/releases.page').then((m) => m.ReleasesPage),
      },
      {
        path: 'releases/new',
        loadComponent: () => import('./features/repo/releases/create-release.page').then((m) => m.CreateReleasePage),
        canActivate: [repoOwnerGuard],
      },
      {
        path: 'releases/:id/edit',
        loadComponent: () => import('./features/repo/releases/edit-release.page').then((m) => m.EditReleasePage),
        canActivate: [repoOwnerGuard],
      },
      {
        path: 'releases/tag/:tagName',
        loadComponent: () => import('./features/repo/releases/release-detail.page').then((m) => m.ReleaseDetailPage),
      },
      {
        path: 'actions',
        loadComponent: () => import('./features/repo/actions/actions.page').then((m) => m.ActionsPage),
      },
      {
        path: 'actions/workflow',
        loadComponent: () => import('./features/repo/actions/workflow-detail.page').then((m) => m.WorkflowDetailPage),
      },
      {
        path: 'actions/runs/:runId',
        loadComponent: () => import('./features/repo/actions/run-detail.page').then((m) => m.RunDetailPage),
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/repo/settings/repo-settings.page').then((m) => m.RepoSettingsPage),
        canActivate: [repoSettingsGuard],
      },
      {
        path: 'my-permissions',
        loadComponent: () =>
          import('./features/repo/my-permissions/repo-my-permissions.page').then((m) => m.RepoMyPermissionsPage),
      },
    ],
  },
];
