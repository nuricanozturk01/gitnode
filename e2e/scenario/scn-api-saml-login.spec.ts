/**
 * SCN-SAML — SAML2 SSO via samltest.dev IdP.
 *
 * Requires:
 * - Backend with gitnode.sso.saml.enabled=true and SP signing key/cert
 * - Platform admin (bootstrap admin by default)
 * - Network access to https://www.samltest.dev
 * - E2E_SAML_ENABLED=1
 *
 * API-only scenario tests (same style as other scenario specs — no browser).
 * Provisions a fresh samltest.dev app + admin organization per run.
 */
import type { APIRequestContext } from '@playwright/test';

import { expect, test } from './fixtures/scenario';
import { getApiBaseUrl, isSamlE2eEnabled } from './helpers/env';
import {
  initiateSpAuthnRequest,
  provisionSamlE2eScenario,
  type SamlDiscoverResponse,
  type SamlE2eContext,
  teardownSamlE2eScenario,
} from './helpers/saml-e2e';

const samlEnabled = isSamlE2eEnabled();

test.describe('SCN-SAML — SAML2 login via samltest.dev', () => {
  test.describe.configure({ mode: 'serial' });
  test.skip(!samlEnabled, 'Set E2E_SAML_ENABLED=1 to run SAML scenario tests');

  let samlContext: SamlE2eContext;
  let setupRequest: APIRequestContext;

  test.beforeAll(async ({ playwright }) => {
    setupRequest = await playwright.request.newContext({ baseURL: getApiBaseUrl() });
    samlContext = await provisionSamlE2eScenario(setupRequest);
  });

  test.afterAll(async () => {
    if (!setupRequest) {
      return;
    }

    try {
      if (samlContext) {
        await teardownSamlE2eScenario(setupRequest, samlContext);
      }
    } finally {
      await setupRequest.dispose();
    }
  });

  test('SCN-SAML-01 — discover returns registration for SSO-enabled org domain', async ({
    bareApi,
  }) => {
    const response = await bareApi.get('/api/auth/sso/saml/discover', {
      params: { email: samlContext.email },
    });

    expect(response.status()).toBe(200);
    const body = (await response.json()) as SamlDiscoverResponse;
    expect(body.orgSlug).toBe(samlContext.slug);
    expect(body.registrationId).toBe(samlContext.slug);
    expect(body.redirectUrl).toBe(samlContext.redirectUrl);
  });

  test('SCN-SAML-02 — discover returns not found for unknown email domain', async ({ bareApi }) => {
    const response = await bareApi.get('/api/auth/sso/saml/discover', {
      params: { email: samlContext.unknownDomainEmail },
    });

    expect(response.status()).toBe(404);
  });

  test('SCN-SAML-03 — admin SSO metadata test and enabled org configuration', async ({
    bareApi,
  }) => {
    const testResponse = await bareApi.post(
      `/api/admin/organizations/${samlContext.slug}/sso/test`,
      {
        headers: { Authorization: samlContext.admin.authorization },
      },
    );
    expect(testResponse.status()).toBe(200);
    const testBody = (await testResponse.json()) as { valid: boolean; cached: boolean };
    expect(testBody.valid).toBe(true);
    expect(testBody.cached).toBe(true);

    const orgResponse = await bareApi.get(`/api/admin/organizations/${samlContext.slug}`, {
      headers: { Authorization: samlContext.admin.authorization },
    });
    expect(orgResponse.status()).toBe(200);
    const org = (await orgResponse.json()) as {
      slug: string;
      ssoEnabled: boolean;
      metadataCached: boolean;
      emailDomains: string[];
    };
    expect(org.slug).toBe(samlContext.slug);
    expect(org.ssoEnabled).toBe(true);
    expect(org.metadataCached).toBe(true);
    expect(org.emailDomains).toContain(samlContext.domain);
  });

  test('SCN-SAML-04 — SP-initiated AuthnRequest reaches samltest.dev login', async ({
    bareApi,
  }) => {
    const result = await initiateSpAuthnRequest(bareApi, samlContext);

    expect(result.acsUrl).toBe(`${getApiBaseUrl()}/login/saml2/sso/${samlContext.slug}`);
    expect(result.idpLoginUrl).toContain('samltest.dev');
    expect(result.idpLoginUrl).toContain(samlContext.samlTestApp.id);
    expect(result.idpLoginUrl).toContain('/login');
  });
});
