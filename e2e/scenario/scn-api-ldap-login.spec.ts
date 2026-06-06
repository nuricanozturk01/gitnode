/**
 * SCN-LDAP — Enterprise LDAP SSO via Docker test directory.
 *
 * Requires:
 * - Backend with originhub.sso.ldap.enabled=true
 * - Platform admin (bootstrap admin by default)
 * - Reachable LDAP server (default: ghcr.io/rroemhild/docker-test-openldap on localhost:389)
 * - E2E_LDAP_ENABLED=1
 *
 * API-only scenario tests (same style as SCN-SAML — no browser).
 * Provisions a fresh admin organization per run; tears down in afterAll.
 */
import type { APIRequestContext } from '@playwright/test';

import { expect, test } from './fixtures/scenario';
import { getApiBaseUrl, isLdapE2eEnabled } from './helpers/env';
import {
  type LdapDiscoverResponse,
  type LdapE2eContext,
  ldapLogin,
  provisionLdapE2eScenario,
  teardownLdapE2eScenario,
} from './helpers/ldap-e2e';

const ldapEnabled = isLdapE2eEnabled();

test.describe('SCN-LDAP — LDAP login via test OpenLDAP', () => {
  test.describe.configure({ mode: 'serial' });
  test.skip(!ldapEnabled, 'Set E2E_LDAP_ENABLED=1 to run LDAP scenario tests');

  let ldapContext: LdapE2eContext;
  let setupRequest: APIRequestContext;

  test.beforeAll(async ({ playwright }) => {
    setupRequest = await playwright.request.newContext({ baseURL: getApiBaseUrl() });
    ldapContext = await provisionLdapE2eScenario(setupRequest);
  });

  test.afterAll(async () => {
    if (!setupRequest) {
      return;
    }

    try {
      if (ldapContext) {
        await teardownLdapE2eScenario(setupRequest, ldapContext);
      }
    } finally {
      await setupRequest.dispose();
    }
  });

  test('SCN-LDAP-01 — discover returns org for LDAP-enabled email domain', async ({ bareApi }) => {
    const response = await bareApi.get('/api/auth/sso/ldap/discover', {
      params: { email: ldapContext.email },
    });

    expect(response.status()).toBe(200);
    const body = (await response.json()) as LdapDiscoverResponse;
    expect(body.orgSlug).toBe(ldapContext.slug);
    expect(body.orgName).toContain('LDAP E2E');
  });

  test('SCN-LDAP-02 — discover returns not found for unknown email domain', async ({ bareApi }) => {
    const response = await bareApi.get('/api/auth/sso/ldap/discover', {
      params: { email: ldapContext.unknownDomainEmail },
    });

    expect(response.status()).toBe(404);
    expect(await response.text()).toContain('ldapOrgNotFound');
  });

  test('SCN-LDAP-03 — admin LDAP connection test and enabled org configuration', async ({
    bareApi,
  }) => {
    const testResponse = await bareApi.post(
      `/api/admin/organizations/${ldapContext.slug}/ldap/test`,
      { headers: { Authorization: ldapContext.admin.authorization } },
    );
    expect(testResponse.status()).toBe(200);
    const testBody = (await testResponse.json()) as { valid: boolean; message: string };
    expect(testBody.valid).toBe(true);

    const orgResponse = await bareApi.get(`/api/admin/organizations/${ldapContext.slug}`, {
      headers: { Authorization: ldapContext.admin.authorization },
    });
    expect(orgResponse.status()).toBe(200);
    const org = (await orgResponse.json()) as {
      slug: string;
      ldapEnabled: boolean;
      ldapConfigured: boolean;
      emailDomains: string[];
    };
    expect(org.slug).toBe(ldapContext.slug);
    expect(org.ldapEnabled).toBe(true);
    expect(org.ldapConfigured).toBe(true);
    expect(org.emailDomains).toContain(ldapContext.domain);
  });

  test('SCN-LDAP-04 — LDAP login returns JWT for valid directory credentials', async ({
    bareApi,
  }) => {
    const loginInfo = await ldapLogin(
      bareApi,
      {
        email: ldapContext.email,
        username: ldapContext.username,
        password: ldapContext.password,
      },
      { trackIn: ldapContext },
    );

    expect(loginInfo.username).toBe(ldapContext.username);
    expect(loginInfo.email).toBeTruthy();
    expect(loginInfo.token).toBeTruthy();
    expect(loginInfo.refreshToken).toBeTruthy();
    expect(loginInfo.expiresIn).toBeGreaterThan(0);
  });

  test('SCN-LDAP-05 — LDAP login rejects invalid password', async ({ bareApi }) => {
    const response = await bareApi.post('/api/auth/sso/ldap/login', {
      data: {
        email: ldapContext.email,
        username: ldapContext.username,
        password: 'definitely-wrong-password',
      },
    });

    expect(response.status()).toBe(403);
    expect(await response.text()).toContain('wrongPassword');
  });

  test('SCN-LDAP-06 — LDAP JWT authorizes authenticated API requests', async ({ bareApi }) => {
    const loginInfo = await ldapLogin(
      bareApi,
      {
        email: ldapContext.email,
        username: ldapContext.username,
        password: ldapContext.password,
      },
      { trackIn: ldapContext },
    );

    const meResponse = await bareApi.get('/api/users/me', {
      headers: { Authorization: `Bearer ${loginInfo.token}` },
    });

    expect(meResponse.status()).toBe(200);
    const profile = (await meResponse.json()) as { username: string; email: string };
    expect(profile.username).toBe(ldapContext.username);
    expect(profile.email).toBeTruthy();
  });
});
