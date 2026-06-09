import type { APIRequestContext } from '@playwright/test';

import { formatLdapConnectionTestError } from './ldap-preflight';

export interface AdminLoginInfo {
  username: string;
  email: string;
  token: string;
  refreshToken: string;
  platformAdmin: boolean;
}

export interface OrganizationInfo {
  slug: string;
  name: string;
  emailDomains: string[];
  ssoEnabled: boolean;
  ldapEnabled?: boolean;
  ldapConfigured?: boolean;
}

export interface SamlMetadataTestResult {
  valid: boolean;
  message: string;
  cached: boolean;
}

export interface LdapConnectionTestResult {
  valid: boolean;
  message: string;
}

export interface OrganizationLdapConfig {
  url: string;
  baseDn: string;
  managerDn: string;
  managerPassword: string;
  userSearchBase: string;
  userSearchFilter: string;
  emailAttribute?: string;
  displayNameAttribute?: string;
  useStartTls?: boolean;
}

export interface AdminSession {
  authorization: string;
  username: string;
}

interface OrganizationPage {
  content: OrganizationInfo[];
  totalPages: number;
}

export async function listOrganizations(
  request: APIRequestContext,
  admin: AdminSession,
  page = 0,
  size = 100,
): Promise<OrganizationInfo[]> {
  const response = await request.get('/api/admin/organizations', {
    headers: { Authorization: admin.authorization },
    params: { page: String(page), size: String(size) },
  });

  if (!response.ok()) {
    throw new Error(`list organizations failed (${response.status()}): ${await response.text()}`);
  }

  const body = (await response.json()) as OrganizationPage;
  return body.content;
}

export async function listAllOrganizations(
  request: APIRequestContext,
  admin: AdminSession,
): Promise<OrganizationInfo[]> {
  const all: OrganizationInfo[] = [];
  let page = 0;

  while (true) {
    const response = await request.get('/api/admin/organizations', {
      headers: { Authorization: admin.authorization },
      params: { page: String(page), size: '100' },
    });

    if (!response.ok()) {
      throw new Error(`list organizations failed (${response.status()}): ${await response.text()}`);
    }

    const body = (await response.json()) as OrganizationPage;
    all.push(...body.content);

    page += 1;
    if (page >= body.totalPages) {
      break;
    }
  }

  return all;
}

export async function adminLogin(
  request: APIRequestContext,
  username: string,
  password: string,
): Promise<AdminSession> {
  const response = await request.post('/api/admin/auth/login', {
    data: { usernameOrEmail: username, password },
  });

  if (!response.ok()) {
    throw new Error(`admin login failed (${response.status()}): ${await response.text()}`);
  }

  const body = (await response.json()) as AdminLoginInfo;

  if (!body.platformAdmin) {
    throw new Error(`user "${username}" is not a platform admin`);
  }

  return {
    authorization: `Bearer ${body.token}`,
    username: body.username,
  };
}

export async function createOrganization(
  request: APIRequestContext,
  admin: AdminSession,
  data: { name: string; slug: string; emailDomains: string[] },
): Promise<OrganizationInfo> {
  const response = await request.post('/api/admin/organizations', {
    headers: { Authorization: admin.authorization },
    data,
  });

  if (!response.ok()) {
    throw new Error(`create organization failed (${response.status()}): ${await response.text()}`);
  }

  return (await response.json()) as OrganizationInfo;
}

export async function configureOrganizationSso(
  request: APIRequestContext,
  admin: AdminSession,
  slug: string,
  data: {
    idpMetadataUri: string;
    emailAttribute?: string;
    spEntityId?: string;
  },
): Promise<OrganizationInfo> {
  const response = await request.put(`/api/admin/organizations/${slug}/sso`, {
    headers: { Authorization: admin.authorization },
    data: {
      ssoEnabled: false,
      idpMetadataUri: data.idpMetadataUri,
      emailAttribute: data.emailAttribute ?? 'email',
      usernameAttribute: null,
      spEntityId: data.spEntityId ?? 'gitnode',
    },
  });

  if (!response.ok()) {
    throw new Error(`configure SSO failed (${response.status()}): ${await response.text()}`);
  }

  return (await response.json()) as OrganizationInfo;
}

export async function testOrganizationSso(
  request: APIRequestContext,
  admin: AdminSession,
  slug: string,
): Promise<SamlMetadataTestResult> {
  const response = await request.post(`/api/admin/organizations/${slug}/sso/test`, {
    headers: { Authorization: admin.authorization },
  });

  if (!response.ok()) {
    throw new Error(`SSO test failed (${response.status()}): ${await response.text()}`);
  }

  return (await response.json()) as SamlMetadataTestResult;
}

export async function setOrganizationSsoEnabled(
  request: APIRequestContext,
  admin: AdminSession,
  slug: string,
  enabled: boolean,
): Promise<OrganizationInfo> {
  const response = await request.put(`/api/admin/organizations/${slug}/sso/enabled`, {
    headers: { Authorization: admin.authorization },
    data: { enabled },
  });

  if (!response.ok()) {
    throw new Error(`set SSO enabled failed (${response.status()}): ${await response.text()}`);
  }

  return (await response.json()) as OrganizationInfo;
}

export async function deleteOrganization(
  request: APIRequestContext,
  admin: AdminSession,
  slug: string,
): Promise<void> {
  const response = await request.delete(`/api/admin/organizations/${slug}`, {
    headers: { Authorization: admin.authorization },
  });

  if (response.status() !== 204 && response.status() !== 404) {
    throw new Error(`delete organization failed (${response.status()}): ${await response.text()}`);
  }
}

export async function deleteSelfUser(
  request: APIRequestContext,
  authorization: string,
): Promise<void> {
  const response = await request.delete('/api/users/me', {
    headers: { Authorization: authorization },
  });

  if (response.status() !== 204 && response.status() !== 404) {
    throw new Error(`delete user failed (${response.status()}): ${await response.text()}`);
  }
}

export async function configureOrganizationLdap(
  request: APIRequestContext,
  admin: AdminSession,
  slug: string,
  data: OrganizationLdapConfig & { ldapEnabled: boolean },
): Promise<OrganizationInfo> {
  const response = await request.put(`/api/admin/organizations/${slug}/ldap`, {
    headers: { Authorization: admin.authorization },
    data: {
      ldapEnabled: data.ldapEnabled,
      url: data.url,
      baseDn: data.baseDn,
      managerDn: data.managerDn,
      managerPassword: data.managerPassword,
      userSearchBase: data.userSearchBase,
      userSearchFilter: data.userSearchFilter,
      emailAttribute: data.emailAttribute ?? 'mail',
      displayNameAttribute: data.displayNameAttribute ?? 'cn',
      useStartTls: data.useStartTls ?? false,
      groupSearchBase: null,
      groupSearchFilter: null,
      groupRoleAttribute: null,
      adminGroupDns: null,
    },
  });

  if (!response.ok()) {
    throw new Error(`configure LDAP failed (${response.status()}): ${await response.text()}`);
  }

  return (await response.json()) as OrganizationInfo;
}

export async function testOrganizationLdap(
  request: APIRequestContext,
  admin: AdminSession,
  slug: string,
): Promise<LdapConnectionTestResult> {
  const response = await request.post(`/api/admin/organizations/${slug}/ldap/test`, {
    headers: { Authorization: admin.authorization },
  });

  if (!response.ok()) {
    const body = await response.text();
    throw new Error(formatLdapConnectionTestError(response.status(), body));
  }

  return (await response.json()) as LdapConnectionTestResult;
}

export async function setOrganizationLdapEnabled(
  request: APIRequestContext,
  admin: AdminSession,
  slug: string,
  enabled: boolean,
): Promise<OrganizationInfo> {
  const response = await request.put(`/api/admin/organizations/${slug}/ldap/enabled`, {
    headers: { Authorization: admin.authorization },
    data: { enabled },
  });

  if (!response.ok()) {
    throw new Error(`set LDAP enabled failed (${response.status()}): ${await response.text()}`);
  }

  return (await response.json()) as OrganizationInfo;
}
