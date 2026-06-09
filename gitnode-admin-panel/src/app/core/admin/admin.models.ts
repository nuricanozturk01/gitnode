import type { TokenResponse } from '../organization/organization.models';

export interface AdminLoginResponse extends TokenResponse {
  platformAdmin?: boolean;
}

export interface AdminUser {
  id: string;
  username: string;
  email: string;
  enabled: boolean;
  createdAt: string;
}

export interface AdminUserDetail extends AdminUser {
  displayName: string | null;
  updatedAt: string;
  platformAdmin: boolean;
  bootstrapAdmin: boolean;
}

export interface AdminAccountProfile {
  id: string;
  username: string;
  email: string;
  displayName: string | null;
  avatarUrl: string | null;
  bio: string | null;
  website: string | null;
  location: string | null;
  profileReadme: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SystemHealthComponent {
  status: string;
  details?: Record<string, unknown>;
}

export interface SystemHealthResponse {
  status: string;
  groups?: string[];
  components?: Record<string, SystemHealthComponent>;
}

export interface SetUserEnabledRequest {
  enabled: boolean;
}

export interface StatsOverview {
  totalUsers: number;
  enabledUsers?: number;
  totalRepos: number;
  totalStorageBytes: number;
  storageEstimated?: boolean;
  storageNote?: string;
  newUsersToday?: number;
  newUsersThisWeek?: number;
  newReposToday?: number;
  newReposThisWeek?: number;
  totalOrganizations?: number;
  ssoEnabledOrganizations?: number;
}

export interface StatsOverviewResponse {
  overview: StatsOverview;
  cachedAt: string;
  cacheTtlSeconds: number;
  fromCache: boolean;
}

export interface AdminPlatformSettings {
  statsCacheTtlSeconds: number;
  pgAuditViewerEnabled: boolean;
  modulithEventsViewerEnabled: boolean;
}

export interface AdminFeatureTogglesRequest {
  pgAuditViewerEnabled: boolean;
  modulithEventsViewerEnabled: boolean;
}

export interface PlatformAdminsResponse {
  usernames: string[];
  bootstrapAdminUsername: string;
  bootstrapAdminEnabled: boolean;
}

export interface AdminRepoSummary {
  id: string;
  ownerUsername: string;
  name: string;
  fullName: string;
  isPrivate: boolean;
  isArchived: boolean;
  defaultBranch: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WebhookDlqEntry {
  id: string;
  webhookId: string;
  url: string;
  eventType: string | null;
  errorMessage: string | null;
  attemptCount: number;
  dlqRetryCount: number;
  nextRetryAt: string | null;
  failedAt: string;
}

export interface WebhookDlqSummary {
  pending: number;
}

export interface ActuatorMetricResponse {
  name: string;
  measurements?: { statistic: string; value: number }[];
  availableTags?: { tag: string; values: string[] }[];
}

export interface ActuatorMetricsIndex {
  names: string[];
}

export type StatsPeriod = 'day' | 'week';

export interface StatsContributor {
  username: string;
  count: number;
}

export interface StatsActivityPoint {
  date: string;
  repos: number;
  uploads: number;
}

export interface StatsReposResponse {
  period: StatsPeriod;
  contributors: StatsContributor[];
  activity: StatsActivityPoint[];
}

export interface StatsUploadsResponse {
  period: StatsPeriod;
  activity: StatsActivityPoint[];
}

export interface AuditLogEntry {
  id: string;
  actorUsername: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: string | null;
  ipAddress: string | null;
  occurredAt: string;
}

export interface AuditLogFilters {
  actions: string[];
  entityTypes: string[];
}

export type AuditPeriodPreset = 'all' | '24h' | '7d' | '30d';

export interface AuditLogQuery {
  page?: number;
  size?: number;
  q?: string;
  actor?: string;
  action?: string;
  entityType?: string;
  entityId?: string;
  ipAddress?: string;
  from?: string;
  to?: string;
}

export interface PgAuditLogEntry {
  id: string;
  occurredAt: string;
  dbUser: string | null;
  database: string | null;
  client: string | null;
  category: string | null;
  command: string | null;
  objectType: string | null;
  objectName: string | null;
  statement: string | null;
  rawLine: string;
}

export interface PgAuditLogStatus {
  viewerEnabled: boolean;
  available: boolean;
  reason: PgAuditStatusReason;
  logDirectory: string | null;
  message: string | null;
}

export type PgAuditStatusReason =
  | 'READY'
  | 'VIEWER_DISABLED'
  | 'LOG_DIRECTORY_NOT_CONFIGURED'
  | 'LOG_DIRECTORY_NOT_FOUND'
  | 'LOG_DIRECTORY_NOT_READABLE';

export interface PgAuditLogSearchResponse {
  content: PgAuditLogEntry[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  available: boolean;
  availabilityMessage: string | null;
}

export type PgAuditCategory = 'WRITE' | 'DDL' | 'ROLE' | 'READ' | 'MISC';

export interface PgAuditLogQuery {
  page?: number;
  size?: number;
  q?: string;
  user?: string;
  category?: string;
  from?: string;
  to?: string;
}

export type AuditLogTab = 'application' | 'database';

export type ModulithEventLifecycleFilter = 'ALL' | 'COMPLETED' | 'IN_PROGRESS' | 'INCOMPLETE' | 'FAILED';

export interface ModulithEventSummary {
  id: string;
  listenerId: string;
  eventType: string;
  publicationDate: string;
  completionDate: string | null;
  status: string | null;
  completionAttempts: number | null;
  lastResubmissionDate: string | null;
  eventPreview: string | null;
}

export interface ModulithEventDetail extends ModulithEventSummary {
  serializedEvent: string;
}

export interface ModulithEventFilters {
  eventTypes: string[];
  listenerIds: string[];
  statuses: string[];
  truncated: boolean;
}

export interface ModulithEventSearchResponse {
  content: ModulithEventSummary[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  available: boolean;
  availabilityMessage: string | null;
}

export interface ModulithEventQuery {
  page?: number;
  size?: number;
  q?: string;
  eventType?: string;
  listenerId?: string;
  status?: string;
  lifecycle?: ModulithEventLifecycleFilter;
  from?: string;
  to?: string;
}
