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
import { authErrorMessage } from './api-error.utils';

describe('authErrorMessage for disabled SSO', () => {
  const notFound = (body: unknown) => new HttpErrorResponse({ status: 404, error: body });

  it('explains that SSO is disabled instead of echoing the raw Spring error', () => {
    const raw = { message: "No static resource api/auth/sso/ldap/discover for request '/api/auth/sso/ldap/discover'." };
    expect(authErrorMessage(notFound(raw), 'ldap', 'fail')).toContain('not enabled on this server');
    expect(authErrorMessage(notFound(raw), 'saml', 'fail')).toContain('not enabled on this server');
  });

  it('keeps known error codes untouched', () => {
    expect(authErrorMessage(notFound({ message: 'ldapOrgNotFound' }), 'ldap', 'fail')).toBe(
      'No LDAP organization found for this email domain.',
    );
    expect(authErrorMessage(notFound({ message: 'samlOrgNotFound' }), 'saml', 'fail')).toBe(
      'No SSO configuration found for this email domain.',
    );
  });

  it('does not change standard login errors', () => {
    expect(authErrorMessage(notFound({ message: 'userNotExist' }), 'standard', 'fail')).toBe(
      'Invalid username or password.',
    );
  });
});
