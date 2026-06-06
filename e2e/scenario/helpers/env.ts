export function getApiBaseUrl(): string {
  return process.env.ORIGINHUB_API_BASE_URL ?? 'http://localhost:8080';
}

export function getFrontendBaseUrl(): string {
  return process.env.ORIGINHUB_FRONTEND_BASE_URL ?? 'http://localhost:4200';
}

export function isSamlE2eEnabled(): boolean {
  return process.env.E2E_SAML_ENABLED === '1';
}

export function isLdapE2eEnabled(): boolean {
  return process.env.E2E_LDAP_ENABLED === '1';
}

export interface LdapServerConfig {
  url: string;
  baseDn: string;
  managerDn: string;
  managerPassword: string;
  userSearchBase: string;
  userSearchFilter: string;
  emailAttribute: string;
  displayNameAttribute: string;
  domain: string;
  testUsername: string;
  testPassword: string;
  testEmail: string;
}

/** Defaults match ghcr.io/rroemhild/docker-test-openldap (Planet Express / port 389 mapped from 10389). */
export function getLdapServerConfig(): LdapServerConfig {
  const domain = process.env.E2E_LDAP_DOMAIN ?? 'planetexpress.com';
  const testUsername = process.env.E2E_LDAP_TEST_USERNAME ?? 'fry';
  const testPassword = process.env.E2E_LDAP_TEST_PASSWORD ?? 'fry';

  return {
    url: process.env.E2E_LDAP_URL ?? 'ldap://localhost:389',
    baseDn: process.env.E2E_LDAP_BASE_DN ?? 'dc=planetexpress,dc=com',
    managerDn: process.env.E2E_LDAP_MANAGER_DN ?? 'cn=admin,dc=planetexpress,dc=com',
    managerPassword: process.env.E2E_LDAP_MANAGER_PASSWORD ?? 'GoodNewsEveryone',
    userSearchBase: process.env.E2E_LDAP_USER_SEARCH_BASE ?? 'ou=people',
    userSearchFilter: process.env.E2E_LDAP_USER_SEARCH_FILTER ?? '(uid={0})',
    emailAttribute: process.env.E2E_LDAP_EMAIL_ATTRIBUTE ?? 'mail',
    displayNameAttribute: process.env.E2E_LDAP_DISPLAY_NAME_ATTRIBUTE ?? 'cn',
    domain,
    testUsername,
    testPassword,
    testEmail: process.env.E2E_LDAP_TEST_EMAIL ?? `${testUsername}@${domain}`,
  };
}

export interface AdminCredentials {
  username: string;
  password: string;
}

export function getAdminCredentials(): AdminCredentials {
  return {
    username: process.env.E2E_ADMIN_USERNAME ?? 'admin',
    password: process.env.E2E_ADMIN_PASSWORD ?? 'Admin123',
  };
}

export const SAMLTEST_DEV_API = 'https://www.samltest.dev/api/apps';
