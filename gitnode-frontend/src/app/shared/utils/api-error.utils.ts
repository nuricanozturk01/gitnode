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

import { HttpErrorResponse } from '@angular/common/http';

export type AuthErrorContext = 'standard' | 'ldap' | 'saml';

const COMMON_AUTH_ERRORS: Record<string, string> = {
  userDisabled: 'This account has been disabled.',
  validationError: 'Invalid request. Please check your input.',
  errorOccurred: 'Something went wrong. Please try again.',
  tooManyRequests: 'Too many attempts. Please wait and try again.',
  invalidEmail: 'Enter a valid email address.',
};

const STANDARD_AUTH_ERRORS: Record<string, string> = {
  ...COMMON_AUTH_ERRORS,
  wrongPassword: 'Invalid username or password.',
  userNotExist: 'Invalid username or password.',
};

const LDAP_AUTH_ERRORS: Record<string, string> = {
  ...COMMON_AUTH_ERRORS,
  wrongPassword: 'Invalid LDAP username or password.',
  ldapOrgNotFound: 'No LDAP organization found for this email domain.',
  ldapUserSearchBaseInvalid: 'LDAP user search base is misconfigured. Contact your administrator.',
  ldapConnectionFailed: 'Could not connect to the LDAP server. Contact your administrator.',
  ldapConfigIncomplete: 'LDAP is not fully configured for this organization.',
};

const SAML_AUTH_ERRORS: Record<string, string> = {
  ...COMMON_AUTH_ERRORS,
  samlOrgNotFound: 'No SSO configuration found for this email domain.',
  idpMetadataFetchFailed: 'Could not load SSO configuration. Contact your administrator.',
  idpMetadataInvalid: 'SSO configuration is invalid. Contact your administrator.',
};

const REGISTRATION_ERRORS: Record<string, string> = {
  usernameInUse: 'This username is already taken.',
  emailInUse: 'An account with this email already exists.',
  validationError: 'Invalid request. Please check your input.',
  errorOccurred: 'Something went wrong. Please try again.',
};

const PROFILE_ERRORS: Record<string, string> = {
  wrongPassword: 'Current password is incorrect.',
  validationError: 'Invalid request. Please check your input.',
  errorOccurred: 'Something went wrong. Please try again.',
};

const AUTH_ERROR_MAPS: Record<AuthErrorContext, Record<string, string>> = {
  standard: STANDARD_AUTH_ERRORS,
  ldap: LDAP_AUTH_ERRORS,
  saml: SAML_AUTH_ERRORS,
};

export function parseBackendErrorCode(body: unknown): string | null {
  if (typeof body === 'string' && body.length > 0) {
    return body;
  }

  if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
    return body.message;
  }

  return null;
}

export function apiErrorMessage(error: unknown, fallback: string, messageMap: Record<string, string> = {}): string {
  if (!(error instanceof HttpErrorResponse)) {
    return (error as Error).message ?? fallback;
  }

  const code = parseBackendErrorCode(error.error);
  if (code) {
    return messageMap[code] ?? code;
  }

  if (error.status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }

  if (error.status === 429) {
    return messageMap.tooManyRequests ?? COMMON_AUTH_ERRORS.tooManyRequests;
  }

  return error.message || error.statusText || fallback;
}

export function authErrorMessage(error: unknown, context: AuthErrorContext, fallback: string): string {
  return apiErrorMessage(error, fallback, AUTH_ERROR_MAPS[context]);
}

export function authQueryErrorMessage(code: string | null): string | null {
  if (!code) {
    return null;
  }

  return STANDARD_AUTH_ERRORS[code] ?? LDAP_AUTH_ERRORS[code] ?? SAML_AUTH_ERRORS[code] ?? code;
}

export function registrationErrorMessage(error: unknown, fallback: string): string {
  return apiErrorMessage(error, fallback, REGISTRATION_ERRORS);
}

export function profileErrorMessage(error: unknown, fallback: string): string {
  return apiErrorMessage(error, fallback, PROFILE_ERRORS);
}
