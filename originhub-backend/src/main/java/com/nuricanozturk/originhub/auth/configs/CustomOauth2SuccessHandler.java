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
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomOauth2SuccessHandler implements AuthenticationSuccessHandler {

  private static final @NonNull String LOGIN = "login";
  private static final @NonNull String AVATAR_URL = "avatar_url";
  private static final @NonNull String PICTURE = "picture";
  private static final @NonNull String SUB = "sub";
  private static final @NonNull String EMAIL = "email";
  private static final @NonNull String USERNAME = "username";
  private static final @NonNull String ID = "id";
  private static final @NonNull String GOOGLE = "google";
  private static final @NonNull String GITHUB = "github";
  private static final @NonNull String GITLAB = "gitlab";
  private static final @NonNull String TOKEN = "token";
  private static final @NonNull String REFRESH_TOKEN = "refresh_token";
  private static final @NonNull String EXPIRES_IN = "expires_in";
  private static final @NonNull String REFRESH_EXPIRES_IN = "refresh_expires_in";

  private final @NonNull JwtUtils jwtUtils;
  private final @NonNull TenantRepository tenantRepository;
  private final @NonNull AccountRepository accountRepository;

  @Value("${originhub.frontend.base-url}")
  private String frontendBaseUrl;

  @Override
  public void onAuthenticationSuccess(
      final @NonNull HttpServletRequest request,
      final @NonNull HttpServletResponse response,
      final @NonNull Authentication authentication)
      throws IOException {

    final var oauth2Token = (OAuth2AuthenticationToken) authentication;
    final var oauth2User = oauth2Token.getPrincipal();

    if (oauth2User == null) {
      log.warn("Oauth2 user is null");
      return;
    }

    final var registrationId = oauth2Token.getAuthorizedClientRegistrationId();

    final var providerInfo = OAuthProviderInfo.of(registrationId, oauth2User);

    final var tenant =
        this.tenantRepository
            .findByUsernameOrEmail(providerInfo.email())
            .orElseGet(() -> this.createUserFromOAuth(providerInfo));

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

  private @NonNull Tenant createUserFromOAuth(final @NonNull OAuthProviderInfo providerInfo) {

    final var tenant =
        this.saveTenant(providerInfo.email(), providerInfo.username(), providerInfo.avatarUrl());
    this.saveAccount(providerInfo, tenant);

    log.warn(
        "User registered via {}: {} - {}",
        providerInfo.accountType(),
        tenant.getUsername(),
        tenant.getEmail());

    return tenant;
  }

  private @NonNull Tenant saveTenant(
      final @NonNull String email,
      final @NonNull String username,
      final @NonNull String avatarUrl) {

    final var tenant = new Tenant();
    tenant.setUsername(username);
    tenant.setEmail(email);
    tenant.setAvatarUrl(avatarUrl);
    tenant.setAdmin(true);

    return this.tenantRepository.save(tenant);
  }

  private void saveAccount(
      final @NonNull OAuthProviderInfo providerInfo, final @NonNull Tenant tenant) {

    final var account = new Account();
    account.setAccountType(providerInfo.accountType().toString());
    account.setSubjectId(providerInfo.subjectId());
    account.setUsername(providerInfo.username());
    account.setEmail(providerInfo.email());
    account.setAvatarUrl(providerInfo.avatarUrl());
    account.setTenant(tenant);
    account.setCreatedAt(Instant.now());

    this.accountRepository.save(account);
  }

  private record OAuthProviderInfo(
      String email, String username, String avatarUrl, String subjectId, AccountType accountType) {

    static OAuthProviderInfo of(final String registrationId, final OAuth2User user) {

      return switch (registrationId) {
        case GITHUB ->
            new OAuthProviderInfo(
                resolveEmail(user, LOGIN),
                attr(user, LOGIN),
                attr(user, AVATAR_URL),
                String.valueOf(user.getAttributes().get(ID)),
                AccountType.GITHUB);
        case GITLAB ->
            new OAuthProviderInfo(
                resolveEmail(user, USERNAME),
                attr(user, USERNAME),
                attr(user, AVATAR_URL),
                String.valueOf(user.getAttributes().get(ID)),
                AccountType.GITLAB);
        case GOOGLE ->
            new OAuthProviderInfo(
                attr(user, EMAIL),
                attr(user, EMAIL),
                attr(user, PICTURE),
                attr(user, SUB),
                AccountType.GOOGLE);

        default -> throw new UnsupportedOperationException("unsupportedOauth2Provider");
      };
    }

    private static String resolveEmail(
        final @NonNull OAuth2User user, final @NonNull String fallbackKey) {

      final var email = (String) user.getAttributes().get(EMAIL);

      if (email != null && !email.isBlank()) {
        return email;
      }

      final var username = attr(user, fallbackKey);
      final var shortTs = String.valueOf(Instant.now().getEpochSecond()).substring(6);

      return "%s-%s@originhub-os.com".formatted(username, shortTs);
    }

    private static String attr(final @NonNull OAuth2User user, final @NonNull String key) {

      return (String) user.getAttributes().get(key);
    }
  }
}
