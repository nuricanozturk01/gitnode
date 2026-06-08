/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.gitnode.os.auth.services;

import dev.gitnode.os.auth.entities.Organization;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.DefaultTlsDirContextAuthenticationStrategy;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NullMarked
public class LdapConnectionService {

  public LdapTemplate createTemplate(final Organization organization) {

    this.requireConfigured(organization);
    return new LdapTemplate(this.buildContextSource(organization));
  }

  public void testConnection(final Organization organization) {

    this.requireConfigured(organization);

    final var source = this.buildContextSource(organization);

    try {
      final var managerDn = organization.getLdapManagerDn();
      if (managerDn != null && !managerDn.isBlank()) {
        source.getContext(managerDn.trim(), organization.getLdapManagerPassword()).close();
      } else {
        source.getContext("", "").close();
      }
    } catch (final Exception ex) {
      log.warn(
          "LDAP connection test failed for org {}: {}", organization.getSlug(), ex.getMessage());
      throw new BadRequestException("ldapConnectionFailed");
    }
  }

  public boolean isConfigured(final Organization organization) {

    return organization.getLdapUrl() != null
        && !organization.getLdapUrl().isBlank()
        && organization.getLdapBaseDn() != null
        && !organization.getLdapBaseDn().isBlank();
  }

  private LdapContextSource buildContextSource(final Organization organization) {

    final var source = new LdapContextSource();
    source.setUrl(organization.getLdapUrl().trim());
    source.setBase(organization.getLdapBaseDn().trim());

    final var managerDn = organization.getLdapManagerDn();
    if (managerDn != null && !managerDn.isBlank()) {
      source.setUserDn(managerDn.trim());
      source.setPassword(organization.getLdapManagerPassword());
    }

    if (organization.isLdapUseStartTls()) {
      final var url = organization.getLdapUrl().trim();
      if (url.startsWith("ldaps://")) {
        log.warn(
            "LDAP org {}: use-start-tls=true with ldaps:// URL — StartTLS operates over plain LDAP",
            organization.getSlug());
      }
      source.setAuthenticationStrategy(new DefaultTlsDirContextAuthenticationStrategy());
    }

    source.afterPropertiesSet();
    return source;
  }

  private void requireConfigured(final Organization organization) {

    if (!this.isConfigured(organization)) {
      throw new BadRequestException("ldapConfigIncomplete");
    }
  }

  static @Nullable String blankToNull(final @Nullable String value) {

    return (value != null && !value.isBlank()) ? value.trim() : null;
  }
}
