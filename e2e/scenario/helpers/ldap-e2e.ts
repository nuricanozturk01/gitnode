import type { APIRequestContext } from '@playwright/test';

import {
  adminLogin,
  type AdminSession,
  configureOrganizationLdap,
  createOrganization,
  deleteOrganization,
  listAllOrganizations,
  setOrganizationLdapEnabled,
  testOrganizationLdap,
} from './admin-api';
import { createE2eRunId } from './e2e-run-id';
import { getAdminCredentials, getLdapServerConfig, type LdapServerConfig } from './env';
import { assertLdapTcpReachable } from './ldap-preflight';
import {
  createProvisionedUserTracker,
  deleteTrackedProvisionedUsers,
  type ProvisionedUserTracker,
  trackProvisionedUser,
} from './provisioned-user-cleanup';

export interface LdapE2eContext {
  runId: string;
  slug: string;
  domain: string;
  email: string;
  username: string;
  password: string;
  unknownDomainEmail: string;
  ldap: LdapServerConfig;
  admin: AdminSession;
  provisionedUsers: ProvisionedUserTracker;
}

export interface LdapDiscoverResponse {
  orgSlug: string;
  orgName: string;
}

export interface LdapLoginInfo {
  email: string;
  username: string;
  token: string;
  refreshToken: string;
  expiresIn: number;
  refreshExpiresIn: number;
}

function buildLdapSlug(runId: string): string {
  return `ldape2e${runId}`.slice(0, 32);
}

async function cleanupStaleLdapE2eOrgs(
  request: APIRequestContext,
  admin: AdminSession,
  domain: string,
): Promise<void> {
  const organizations = await listAllOrganizations(request, admin);

  for (const organization of organizations) {
    const sharesDomain = organization.emailDomains.some(
      (emailDomain) => emailDomain.toLowerCase() === domain.toLowerCase(),
    );
    const isStaleE2eOrg = organization.slug.startsWith('ldape2e');

    if (isStaleE2eOrg || (organization.ldapEnabled && sharesDomain)) {
      await deleteOrganization(request, admin, organization.slug);
    }
  }
}

export async function provisionLdapE2eScenario(
  request: APIRequestContext,
): Promise<LdapE2eContext> {
  const runId = createE2eRunId();
  const slug = buildLdapSlug(runId);
  const ldap = getLdapServerConfig();
  const domain = ldap.domain;

  await assertLdapTcpReachable(ldap.url);

  const adminCreds = getAdminCredentials();
  const admin = await adminLogin(request, adminCreds.username, adminCreds.password);

  await cleanupStaleLdapE2eOrgs(request, admin, domain);

  let orgCreated = false;

  try {
    await createOrganization(request, admin, {
      name: `LDAP E2E ${runId}`,
      slug,
      emailDomains: [domain],
    });
    orgCreated = true;

    await configureOrganizationLdap(request, admin, slug, {
      ldapEnabled: false,
      url: ldap.url,
      baseDn: ldap.baseDn,
      managerDn: ldap.managerDn,
      managerPassword: ldap.managerPassword,
      userSearchBase: ldap.userSearchBase,
      userSearchFilter: ldap.userSearchFilter,
      emailAttribute: ldap.emailAttribute,
      displayNameAttribute: ldap.displayNameAttribute,
      useStartTls: false,
    });

    const testResult = await testOrganizationLdap(request, admin, slug);
    if (!testResult.valid) {
      throw new Error(`LDAP connection test failed: ${testResult.message}`);
    }

    await setOrganizationLdapEnabled(request, admin, slug, true);

    return {
      runId,
      slug,
      domain,
      email: ldap.testEmail,
      username: ldap.testUsername,
      password: ldap.testPassword,
      unknownDomainEmail: `nobody-${runId}@unknown-${runId}.local`,
      ldap,
      admin,
      provisionedUsers: createProvisionedUserTracker(),
    };
  } catch (error) {
    if (orgCreated) {
      await deleteOrganization(request, admin, slug);
    }
    throw error;
  }
}

async function deleteProvisionedUsers(
  request: APIRequestContext,
  context: LdapE2eContext,
): Promise<void> {
  if (context.provisionedUsers.provisionedUserTokensByUsername.size === 0) {
    try {
      const login = await ldapLogin(request, {
        email: context.email,
        username: context.username,
        password: context.password,
      });
      trackProvisionedUser(context.provisionedUsers, login);
    } catch {
      return;
    }
  }

  await deleteTrackedProvisionedUsers(request, context.provisionedUsers);
}

export async function teardownLdapE2eScenario(
  request: APIRequestContext,
  context: LdapE2eContext,
): Promise<void> {
  await deleteProvisionedUsers(request, context);
  await deleteOrganization(request, context.admin, context.slug);
}

export function trackLdapProvisionedUser(context: LdapE2eContext, login: LdapLoginInfo): void {
  trackProvisionedUser(context.provisionedUsers, login);
}

export async function ldapLogin(
  request: APIRequestContext,
  credentials: { email: string; username: string; password: string },
  options?: { trackIn?: LdapE2eContext },
): Promise<LdapLoginInfo> {
  const response = await request.post('/api/auth/sso/ldap/login', {
    data: credentials,
  });

  if (!response.ok()) {
    throw new Error(`LDAP login failed (${response.status()}): ${await response.text()}`);
  }

  const loginInfo = (await response.json()) as LdapLoginInfo;
  if (options?.trackIn) {
    trackLdapProvisionedUser(options.trackIn, loginInfo);
  }
  return loginInfo;
}
