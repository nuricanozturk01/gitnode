import { Routes } from '@angular/router';
import { authGuard } from './core/auth/guards/auth.guard';
import { platformAdminGuard } from './core/admin/platform-admin.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    loadComponent: () => import('./layout/admin-layout/admin-layout.component').then((m) => m.AdminLayoutComponent),
    canActivate: [authGuard, platformAdminGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'users',
        loadComponent: () => import('./features/users/list/users-list.page').then((m) => m.UsersListPage),
      },
      {
        path: 'users/:id',
        loadComponent: () => import('./features/users/detail/user-detail.page').then((m) => m.UserDetailPage),
      },
      {
        path: 'repos',
        loadComponent: () => import('./features/repos/list/repos-list.page').then((m) => m.ReposListPage),
      },
      {
        path: 'webhooks/dlq',
        loadComponent: () => import('./features/webhooks/dlq/webhook-dlq.page').then((m) => m.WebhookDlqPage),
      },
      {
        path: 'account',
        loadComponent: () => import('./features/account/account.page').then((m) => m.AccountPage),
      },
      {
        path: 'system',
        loadComponent: () => import('./features/system/system-health.page').then((m) => m.SystemHealthPage),
      },
      {
        path: 'audit-logs',
        loadComponent: () => import('./features/audit/list/audit-logs-list.page').then((m) => m.AuditLogsListPage),
      },
      {
        path: 'pgaudit-logs',
        loadComponent: () => import('./features/pgaudit/pgaudit-logs.page').then((m) => m.PgauditLogsPage),
      },
      {
        path: 'modulith-events',
        loadComponent: () =>
          import('./features/modulith-events/modulith-events.page').then((m) => m.ModulithEventsPage),
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage),
      },
      {
        path: 'actions',
        loadComponent: () => import('./features/actions/actions.page').then((m) => m.ActionsPage),
      },
      {
        path: 'organizations',
        loadComponent: () =>
          import('./features/organizations/list/organizations-list.page').then((m) => m.OrganizationsListPage),
      },
      {
        path: 'organizations/new',
        loadComponent: () =>
          import('./features/organizations/new/organization-new.page').then((m) => m.OrganizationNewPage),
      },
      {
        path: 'organizations/:slug',
        loadComponent: () =>
          import('./features/organizations/detail/organization-detail.page').then((m) => m.OrganizationDetailPage),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
