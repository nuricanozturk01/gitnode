import type { APIRequestContext, APIResponse } from '@playwright/test';

import {
  adminLogin,
  type AdminSession,
  configureOrganizationSso,
  createOrganization,
  deleteOrganization,
  listAllOrganizations,
  setOrganizationSsoEnabled,
  testOrganizationSso,
} from './admin-api';
import { createE2eRunId } from './e2e-run-id';
import { getAdminCredentials, getApiBaseUrl } from './env';
import {
  createProvisionedUserTracker,
  deleteTrackedProvisionedUsers,
  type ProvisionedUserTracker,
  trackProvisionedUser,
} from './provisioned-user-cleanup';
import { parseHtmlForm, resolveUrl } from './saml-http';
import {
  createSamlTestApp,
  deleteSamlTestApp,
  type SamlTestApp,
  samlTestMetadataUri,
} from './samltest-dev';

export interface SamlE2eContext {
  runId: string;
  slug: string;
  domain: string;
  email: string;
  unknownDomainEmail: string;
  redirectUrl: string;
  samlTestApp: SamlTestApp;
  admin: AdminSession;
  provisionedUsers: ProvisionedUserTracker;
}

export interface SamlDiscoverResponse {
  orgSlug: string;
  registrationId: string;
  redirectUrl: string;
}

export interface SpAuthnRequestResult {
  idpLoginUrl: string;
  acsUrl: string;
}

function buildSamlSlug(runId: string): string {
  return `samle2e${runId}`.slice(0, 32);
}

async function cleanupStaleSamlE2eOrgs(
  request: APIRequestContext,
  admin: AdminSession,
): Promise<void> {
  const organizations = await listAllOrganizations(request, admin);

  for (const organization of organizations) {
    if (organization.slug.startsWith('samle2e') || organization.slug.startsWith('saml2e')) {
      await deleteOrganization(request, admin, organization.slug);
    }
  }
}

export async function provisionSamlE2eScenario(
  request: APIRequestContext,
): Promise<SamlE2eContext> {
  const runId = createE2eRunId();
  const slug = buildSamlSlug(runId);
  const domain = `${slug}.local`;
  const email = `e2e-${runId}@${domain}`;
  const apiBase = getApiBaseUrl();
  const acsUrl = `${apiBase}/login/saml2/sso/${slug}`;

  const adminCreds = getAdminCredentials();
  const admin = await adminLogin(request, adminCreds.username, adminCreds.password);

  await cleanupStaleSamlE2eOrgs(request, admin);

  let samlTestApp: SamlTestApp | undefined;
  let orgCreated = false;

  try {
    samlTestApp = await createSamlTestApp({
      spAcsUrl: acsUrl,
      spEntityId: 'originhub',
      users: [{ email, firstName: 'E2E', lastName: runId.slice(0, 12) }],
    });

    await createOrganization(request, admin, {
      name: `SAML E2E ${runId}`,
      slug,
      emailDomains: [domain],
    });
    orgCreated = true;

    await configureOrganizationSso(request, admin, slug, {
      idpMetadataUri: samlTestMetadataUri(samlTestApp.id),
      emailAttribute: 'email',
      spEntityId: 'originhub',
    });

    const testResult = await testOrganizationSso(request, admin, slug);
    if (!testResult.valid) {
      throw new Error(`SSO metadata test failed: ${testResult.message}`);
    }

    await setOrganizationSsoEnabled(request, admin, slug, true);

    return {
      runId,
      slug,
      domain,
      email,
      unknownDomainEmail: `nobody-${runId}@unknown-${runId}.local`,
      redirectUrl: `/saml2/authenticate/${slug}`,
      samlTestApp,
      admin,
      provisionedUsers: createProvisionedUserTracker(),
    };
  } catch (error) {
    if (orgCreated) {
      await deleteOrganization(request, admin, slug);
    }
    if (samlTestApp) {
      await deleteSamlTestApp(samlTestApp.id);
    }
    throw error;
  }
}

export function trackSamlProvisionedUser(
  context: SamlE2eContext,
  login: { username: string; token: string },
): void {
  trackProvisionedUser(context.provisionedUsers, login);
}

async function deleteProvisionedUsers(
  request: APIRequestContext,
  context: SamlE2eContext,
): Promise<void> {
  await deleteTrackedProvisionedUsers(request, context.provisionedUsers);
}

export async function teardownSamlE2eScenario(
  request: APIRequestContext,
  context: SamlE2eContext,
): Promise<void> {
  await deleteProvisionedUsers(request, context);
  await deleteOrganization(request, context.admin, context.slug);
  await deleteSamlTestApp(context.samlTestApp.id);
}

function readRedirectLocation(response: APIResponse, baseUrl: string): string {
  const location = response.headers().location;
  if (!location) {
    throw new Error(`Expected redirect from ${baseUrl}, got status ${response.status()}`);
  }
  return resolveUrl(baseUrl, location);
}

export async function initiateSpAuthnRequest(
  request: APIRequestContext,
  context: SamlE2eContext,
): Promise<SpAuthnRequestResult> {
  const apiBase = getApiBaseUrl();
  const acsUrl = `${apiBase}/login/saml2/sso/${context.slug}`;

  const authnResponse = await request.get(context.redirectUrl, { maxRedirects: 0 });
  if (authnResponse.status() !== 200) {
    throw new Error(
      `SP authenticate endpoint failed (${authnResponse.status()}): ${await authnResponse.text()}`,
    );
  }

  const authnForm = parseHtmlForm(await authnResponse.text());
  if (authnForm?.method !== 'POST') {
    throw new Error('SP authenticate response did not contain a POST form');
  }
  if (!authnForm.fields.SAMLRequest) {
    throw new Error('SP authenticate form missing SAMLRequest');
  }

  if (
    !authnForm.action.includes('samltest.dev') ||
    !authnForm.action.includes(context.samlTestApp.id)
  ) {
    throw new Error(`Unexpected IdP SSO URL in SP form: ${authnForm.action}`);
  }

  const idpSsoResponse = await request.post(authnForm.action, {
    form: authnForm.fields,
    maxRedirects: 0,
  });

  if (idpSsoResponse.status() !== 302) {
    throw new Error(`Expected IdP SSO redirect (302), got ${idpSsoResponse.status()}`);
  }

  const idpLoginUrl = readRedirectLocation(idpSsoResponse, authnForm.action);
  if (!idpLoginUrl.includes('samltest.dev')) {
    throw new Error(`Unexpected IdP login URL: ${idpLoginUrl}`);
  }
  if (!idpLoginUrl.includes('/login')) {
    throw new Error(`Expected samltest.dev login page, got: ${idpLoginUrl}`);
  }

  return { idpLoginUrl, acsUrl };
}
