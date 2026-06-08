export const ADMIN_PAGE_SIZE = 10;
export const AUDIT_PAGE_SIZE = 20;

export interface TokenResponse {
  token: string;
  refreshToken: string;
  expiresIn?: number;
  refreshExpiresIn?: number;
  username?: string;
}

export interface LoginForm {
  usernameOrEmail: string;
  password: string;
}

export interface OrganizationSummary {
  id: string;
  name: string;
  slug: string;
  emailDomains: string[];
  ssoEnabled: boolean;
  ldapEnabled: boolean;
  metadataCached?: boolean;
  ldapConfigured?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

/** Matches backend OrganizationInfo (flat SSO/LDAP fields on detail). */
export interface OrganizationDetail extends OrganizationSummary {
  idpMetadataUri?: string | null;
  emailAttribute?: string;
  usernameAttribute?: string | null;
  spEntityId?: string | null;
  ldapUrl?: string | null;
  ldapBaseDn?: string | null;
  ldapManagerDn?: string | null;
  managerPasswordConfigured?: boolean;
  ldapUserSearchBase?: string;
  ldapUserSearchFilter?: string;
  ldapEmailAttribute?: string;
  ldapDisplayNameAttribute?: string;
  ldapUseStartTls?: boolean;
  ldapGroupSearchBase?: string | null;
  ldapGroupSearchFilter?: string | null;
  ldapGroupRoleAttribute?: string | null;
  ldapAdminGroupDns?: string | null;
}

export interface CreateOrganizationRequest {
  name: string;
  slug: string;
  emailDomains: string[];
}

export interface UpdateOrganizationRequest {
  name: string;
  emailDomains: string[];
}

export interface SsoConfigRequest {
  ssoEnabled: boolean;
  idpMetadataUri: string;
  emailAttribute: string;
  usernameAttribute?: string | null;
  spEntityId?: string | null;
}

export interface LdapConfigRequest {
  ldapEnabled: boolean;
  url: string;
  baseDn: string;
  managerDn?: string | null;
  managerPassword?: string | null;
  userSearchBase: string;
  userSearchFilter: string;
  emailAttribute: string;
  displayNameAttribute: string;
  useStartTls: boolean;
  groupSearchBase?: string | null;
  groupSearchFilter?: string | null;
  groupRoleAttribute?: string | null;
  adminGroupDns?: string | null;
}

export interface SsoEnabledRequest {
  enabled: boolean;
}

/** Backend SamlMetadataTestResult */
export interface SsoTestResult {
  valid: boolean;
  message: string;
  cached: boolean;
}

/** Backend LdapConnectionTestResult */
export interface LdapTestResult {
  valid: boolean;
  message: string;
}

/** Spring PageResponse wrapper. */
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface PageQuery {
  page?: number;
  size?: number;
}

export interface UserPageQuery extends PageQuery {
  q?: string;
}
