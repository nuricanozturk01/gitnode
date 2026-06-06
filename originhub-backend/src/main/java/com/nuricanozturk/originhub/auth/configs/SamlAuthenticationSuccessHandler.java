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
package com.nuricanozturk.originhub.auth.configs;

import com.nuricanozturk.originhub.auth.dtos.AccountType;
import com.nuricanozturk.originhub.auth.entities.Account;
import com.nuricanozturk.originhub.auth.repositories.AccountRepository;
import com.nuricanozturk.originhub.auth.repositories.OrganizationRepository;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@ConditionalOnProperty(name = "originhub.sso.saml.enabled", havingValue = "true")
@RequiredArgsConstructor
@NullMarked
public class SamlAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  private static final String TOKEN = "token";
  private static final String REFRESH_TOKEN = "refresh_token";
  private static final String USERNAME = "username";
  private static final String EXPIRES_IN = "expires_in";
  private static final String REFRESH_EXPIRES_IN = "refresh_expires_in";

  private final JwtUtils jwtUtils;
  private final TenantRepository tenantRepository;
  private final AccountRepository accountRepository;
  private final OrganizationRepository organizationRepository;

  @Value("${originhub.frontend.base-url}")
  private String frontendBaseUrl;

  @Value("${originhub.sso.saml.email-attribute:email}")
  private String defaultEmailAttribute;

  @Value("${originhub.sso.saml.username-attribute:}")
  private String defaultUsernameAttribute;

  @Override
  public void onAuthenticationSuccess(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication)
      throws IOException {

    final var principal = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();
    final var registrationId = this.resolveRegistrationId(authentication);
    final var emailAttribute = this.resolveEmailAttribute(registrationId);
    final var usernameAttribute = this.resolveUsernameAttribute(registrationId);

    final var nameId = Objects.requireNonNull(principal).getName();
    final var email = this.resolveEmail(principal, nameId, emailAttribute);
    final var username = this.resolveUsername(principal, email, usernameAttribute);

    final var tenant =
        this.tenantRepository
            .findByUsernameOrEmail(email.toLowerCase(Locale.getDefault()))
            .orElseGet(() -> this.createTenantFromSaml(nameId, email, username));

    if (!tenant.isEnabled()) {
      this.redirectLoginError(response, "userDisabled");
      return;
    }

    final var redirectUrl =
        UriComponentsBuilder.fromUriString("%s/login".formatted(this.frontendBaseUrl))
            .queryParam(TOKEN, this.jwtUtils.generateToken(tenant))
            .queryParam(REFRESH_TOKEN, this.jwtUtils.generateRefreshToken(tenant))
            .queryParam(USERNAME, tenant.getUsername())
            .queryParam(EXPIRES_IN, JwtUtils.ACCESS_EXPIRATION_SECONDS)
            .queryParam(REFRESH_EXPIRES_IN, JwtUtils.REFRESH_EXPIRATION_SECONDS)
            .build()
            .toUriString();

    response.sendRedirect(redirectUrl);
  }

  private void redirectLoginError(final HttpServletResponse response, final String error)
      throws IOException {

    response.sendRedirect(
        UriComponentsBuilder.fromUriString("%s/login".formatted(this.frontendBaseUrl))
            .queryParam("error", error)
            .build()
            .toUriString());
  }

  private String resolveRegistrationId(final Authentication authentication) {

    if (authentication instanceof Saml2AssertionAuthentication assertionAuthentication) {
      return assertionAuthentication.getRelyingPartyRegistrationId();
    }

    return "";
  }

  private String resolveEmailAttribute(final String registrationId) {

    return this.organizationRepository
        .findBySlug(registrationId)
        .map(org -> org.getEmailAttribute())
        .orElse(this.defaultEmailAttribute);
  }

  private String resolveUsernameAttribute(final String registrationId) {

    return this.organizationRepository
        .findBySlug(registrationId)
        .map(org -> org.getUsernameAttribute())
        .filter(attr -> attr != null && !attr.isBlank())
        .orElse(this.defaultUsernameAttribute);
  }

  private String resolveEmail(
      final Saml2AuthenticatedPrincipal principal,
      final String nameId,
      final String emailAttribute) {

    @Nullable final String fromAttr = principal.getFirstAttribute(emailAttribute);

    if (fromAttr != null && !fromAttr.isBlank()) {
      return fromAttr;
    }

    if (nameId.contains("@")) {
      return nameId;
    }

    final var shortTs = String.valueOf(Instant.now().getEpochSecond()).substring(6);

    return "%s-%s@saml.originhub-os.com".formatted(nameId, shortTs);
  }

  private String resolveUsername(
      final Saml2AuthenticatedPrincipal principal,
      final String email,
      final String usernameAttribute) {

    if (!usernameAttribute.isBlank()) {
      @Nullable final String fromAttr = principal.getFirstAttribute(usernameAttribute);

      if (fromAttr != null && !fromAttr.isBlank()) {
        return fromAttr.toLowerCase(Locale.getDefault());
      }
    }

    return email.split("@")[0].toLowerCase(Locale.getDefault());
  }

  private Tenant createTenantFromSaml(
      final String nameId, final String email, final String username) {

    final var tenant = new Tenant();
    tenant.setUsername(username);
    tenant.setEmail(email.toLowerCase(Locale.getDefault()));
    tenant.setCreatedAt(Instant.now());

    final var saved = this.tenantRepository.save(tenant);

    this.saveAccount(nameId, email, username, saved);

    log.warn("SAML user provisioned: {} - {}", username, email);

    return saved;
  }

  private void saveAccount(
      final String nameId, final String email, final String username, final Tenant tenant) {

    final var account = new Account();
    account.setAccountType(AccountType.SAML.name());
    account.setSubjectId(nameId);
    account.setUsername(username);
    account.setEmail(email.toLowerCase(Locale.getDefault()));
    account.setTenant(tenant);
    account.setCreatedAt(Instant.now());

    this.accountRepository.save(account);
  }
}
