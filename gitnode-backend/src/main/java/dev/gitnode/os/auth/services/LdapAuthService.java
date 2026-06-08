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

import static org.springframework.ldap.query.LdapQueryBuilder.query;

import dev.gitnode.os.auth.dtos.AccountType;
import dev.gitnode.os.auth.dtos.LdapLoginForm;
import dev.gitnode.os.auth.entities.Account;
import dev.gitnode.os.auth.entities.Organization;
import dev.gitnode.os.auth.repositories.AccountRepository;
import dev.gitnode.os.auth.repositories.OrganizationRepository;
import dev.gitnode.os.shared.auth.dtos.LoginInfo;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.auth.services.TenantAuthGuard;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.NamingException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@ConditionalOnProperty(name = "gitnode.sso.ldap.enabled", havingValue = "true")
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class LdapAuthService {

  private final OrganizationRepository organizationRepository;
  private final LdapConnectionService ldapConnectionService;
  private final TenantRepository tenantRepository;
  private final AccountRepository accountRepository;
  private final JwtUtils jwtUtils;

  @Transactional
  public LoginInfo authenticate(final LdapLoginForm form) {

    final var email = form.getEmail().trim().toLowerCase(Locale.getDefault());
    final var username = form.getUsername().trim().toLowerCase(Locale.getDefault());
    final var organization = this.resolveOrganization(email);

    final var ldapTemplate = this.ldapConnectionService.createTemplate(organization);
    final var filterWithUser = organization.getLdapUserSearchFilter().replace("{0}", username);

    try {
      ldapTemplate.authenticate(
          query().base(organization.getLdapUserSearchBase()).filter(filterWithUser),
          form.getPassword());
    } catch (final NamingException ex) {
      log.warn(
          "LDAP authentication failed for user {} (org {}): {}",
          username,
          organization.getSlug(),
          ex.getMessage());
      if (this.isNoSuchObject(ex)) {
        throw new BadRequestException("ldapUserSearchBaseInvalid");
      }
      throw new AccessNotAllowedException("wrongPassword");
    }

    final var userInfo =
        this.fetchLdapUserInfo(organization, ldapTemplate, username, filterWithUser);
    final var groups = this.fetchLdapGroups(organization, ldapTemplate, username);

    final var tenant =
        this.tenantRepository
            .findByUsernameOrEmail(userInfo.email())
            .orElseGet(() -> this.createTenantFromLdap(username, userInfo));

    this.upsertAccount(organization, username, tenant, groups);

    TenantAuthGuard.requireEnabled(tenant);

    return this.buildLoginInfo(tenant);
  }

  private Organization resolveOrganization(final String email) {

    final var atIndex = email.lastIndexOf('@');
    if (atIndex < 1 || atIndex == email.length() - 1) {
      throw new BadRequestException("invalidEmail");
    }

    final var domain = email.substring(atIndex + 1);

    return this.organizationRepository
        .findLdapEnabledByEmailDomain(domain)
        .orElseThrow(() -> new ItemNotFoundException("ldapOrgNotFound"));
  }

  private LdapUserInfo fetchLdapUserInfo(
      final Organization organization,
      final LdapTemplate ldapTemplate,
      final String username,
      final String filter) {

    final var emailAttribute = organization.getLdapEmailAttribute();
    final var displayNameAttribute = organization.getLdapDisplayNameAttribute();

    try {
      final AttributesMapper<LdapUserInfo> mapper =
          attrs -> {
            final var emailAttr = attrs.get(emailAttribute);
            final var displayNameAttr = attrs.get(displayNameAttribute);

            final var resolvedEmail =
                emailAttr != null
                    ? (String) emailAttr.get()
                    : "%s@ldap.gitnode-os.com".formatted(username);

            final var displayName =
                displayNameAttr != null ? (String) displayNameAttr.get() : username;

            return new LdapUserInfo(resolvedEmail, displayName);
          };

      final var results =
          ldapTemplate.search(
              query().base(organization.getLdapUserSearchBase()).filter(filter), mapper);

      if (results.isEmpty()) {
        return new LdapUserInfo("%s@ldap.gitnode-os.com".formatted(username), username);
      }

      return results.getFirst();
    } catch (final NamingException ex) {
      log.warn("LDAP user info fetch failed for {}: {}", username, ex.getMessage());
      return new LdapUserInfo("%s@ldap.gitnode-os.com".formatted(username), username);
    }
  }

  private Tenant createTenantFromLdap(final String username, final LdapUserInfo userInfo) {

    final var tenant = new Tenant();
    tenant.setUsername(username);
    tenant.setEmail(userInfo.email().toLowerCase(Locale.getDefault()));
    tenant.setDisplayName(userInfo.displayName());
    tenant.setCreatedAt(Instant.now());

    final var saved = this.tenantRepository.save(tenant);

    log.warn("LDAP user provisioned: {} - {}", username, userInfo.email());

    return saved;
  }

  private List<String> fetchLdapGroups(
      final Organization organization, final LdapTemplate ldapTemplate, final String username) {

    if (!this.hasGroupSearchConfig(organization)) {
      return List.of();
    }

    final var groupSearchBase = organization.getLdapGroupSearchBase();
    final var groupSearchFilter = organization.getLdapGroupSearchFilter();
    final var groupRoleAttribute = organization.getLdapGroupRoleAttribute();
    final var filter = groupSearchFilter.replace("{0}", username);

    try {
      final AttributesMapper<String> mapper =
          attrs -> {
            final var attr = attrs.get(groupRoleAttribute);
            return attr != null ? (String) attr.get() : null;
          };

      return ldapTemplate.search(query().base(groupSearchBase).filter(filter), mapper).stream()
          .filter(g -> g != null && !g.isBlank())
          .collect(Collectors.toList());
    } catch (final NamingException ex) {
      log.warn("LDAP group search failed for {}: {}", username, ex.getMessage());
      return List.of();
    }
  }

  private boolean hasGroupSearchConfig(final Organization organization) {

    return isNotBlank(organization.getLdapGroupSearchBase())
        && isNotBlank(organization.getLdapGroupSearchFilter())
        && isNotBlank(organization.getLdapGroupRoleAttribute());
  }

  private static boolean isNotBlank(final @Nullable String value) {

    return value != null && !value.isBlank();
  }

  private boolean isNoSuchObject(final NamingException ex) {

    final var message = ex.getMessage();
    return message != null
        && (message.contains("error code 32") || message.contains("No Such Object"));
  }

  private void upsertAccount(
      final Organization organization,
      final String username,
      final Tenant tenant,
      final List<String> groups) {

    final var groupsCsv = groups.isEmpty() ? null : String.join(",", groups);
    final var adminGroupDns = organization.getLdapAdminGroupDns();

    if (!groups.isEmpty() && adminGroupDns != null && !adminGroupDns.isBlank()) {
      final var adminDns = List.of(adminGroupDns.split(","));
      final boolean isAdmin = groups.stream().anyMatch(adminDns::contains);
      if (isAdmin) {
        log.info(
            "LDAP user {} is member of admin group (org {})", username, organization.getSlug());
      }
    }

    this.accountRepository
        .findByAccountTypeAndSubjectId(AccountType.LDAP.name(), username)
        .ifPresentOrElse(
            existing -> {
              existing.setLdapGroups(groupsCsv);
              this.accountRepository.save(existing);
            },
            () -> this.saveAccount(username, tenant, groupsCsv));
  }

  private void saveAccount(final String username, final Tenant tenant, final String groupsCsv) {

    final var account = new Account();
    account.setAccountType(AccountType.LDAP.name());
    account.setSubjectId(username);
    account.setUsername(username);
    account.setEmail(tenant.getEmail());
    account.setTenant(tenant);
    account.setCreatedAt(Instant.now());
    account.setLdapGroups(groupsCsv);

    this.accountRepository.save(account);
  }

  private LoginInfo buildLoginInfo(final Tenant tenant) {

    return LoginInfo.builder()
        .token(this.jwtUtils.generateToken(tenant))
        .refreshToken(this.jwtUtils.generateRefreshToken(tenant))
        .email(tenant.getEmail())
        .username(tenant.getUsername())
        .expiresIn(JwtUtils.ACCESS_EXPIRATION_SECONDS)
        .refreshExpiresIn(JwtUtils.REFRESH_EXPIRATION_SECONDS)
        .build();
  }

  private record LdapUserInfo(String email, String displayName) {}
}
