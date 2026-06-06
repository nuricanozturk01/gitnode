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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.auth.entities.Account;
import com.nuricanozturk.originhub.auth.repositories.AccountRepository;
import com.nuricanozturk.originhub.auth.repositories.OrganizationRepository;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SamlAuthenticationSuccessHandler unit tests")
class SamlAuthenticationSuccessHandlerTest {

  @Mock private JwtUtils jwtUtils;
  @Mock private TenantRepository tenantRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private Saml2Authentication authentication;

  @InjectMocks private SamlAuthenticationSuccessHandler handler;

  @BeforeEach
  void setUp() {

    ReflectionTestUtils.setField(handler, "frontendBaseUrl", "http://localhost:4200");
    ReflectionTestUtils.setField(handler, "defaultEmailAttribute", "email");
    ReflectionTestUtils.setField(handler, "defaultUsernameAttribute", "");
  }

  @Test
  @DisplayName("provisions new tenant when SAML user is unknown and redirects with JWT")
  void onAuthenticationSuccess_createsNewTenant_andRedirects() throws Exception {

    final var principal =
        new DefaultSaml2AuthenticatedPrincipal(
            "user-name-id", Map.of("email", List.of("alice@corp.example.com")));

    when(authentication.getPrincipal()).thenReturn(principal);

    final Tenant saved = new Tenant();
    saved.setId(UUID.randomUUID());
    saved.setUsername("alice");
    saved.setEmail("alice@corp.example.com");

    when(tenantRepository.findByUsernameOrEmail("alice@corp.example.com"))
        .thenReturn(Optional.empty());
    when(tenantRepository.save(any(Tenant.class))).thenReturn(saved);
    when(jwtUtils.generateToken(saved)).thenReturn("jwt-access");
    when(jwtUtils.generateRefreshToken(saved)).thenReturn("jwt-refresh");

    handler.onAuthenticationSuccess(request, response, authentication);

    final ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).sendRedirect(redirectCaptor.capture());

    final String redirectUrl = redirectCaptor.getValue();
    assertThat(redirectUrl).startsWith("http://localhost:4200/login");
    assertThat(redirectUrl).contains("token=jwt-access");
    assertThat(redirectUrl).contains("refresh_token=jwt-refresh");
    assertThat(redirectUrl).contains("username=alice");
    assertThat(redirectUrl).contains("expires_in=");
    assertThat(redirectUrl).contains("refresh_expires_in=");
  }

  @Test
  @DisplayName("finds existing tenant by email and does not create duplicate")
  void onAuthenticationSuccess_usesExistingTenant_whenEmailMatches() throws Exception {

    final var principal =
        new DefaultSaml2AuthenticatedPrincipal(
            "user-name-id", Map.of("email", List.of("alice@corp.example.com")));

    when(authentication.getPrincipal()).thenReturn(principal);

    final Tenant existing = new Tenant();
    existing.setId(UUID.randomUUID());
    existing.setUsername("alice");
    existing.setEmail("alice@corp.example.com");

    when(tenantRepository.findByUsernameOrEmail("alice@corp.example.com"))
        .thenReturn(Optional.of(existing));
    when(jwtUtils.generateToken(existing)).thenReturn("jwt-access");
    when(jwtUtils.generateRefreshToken(existing)).thenReturn("jwt-refresh");

    handler.onAuthenticationSuccess(request, response, authentication);

    verify(tenantRepository, never()).save(any());
    verify(accountRepository, never()).save(any());
    verify(response).sendRedirect(anyString());
  }

  @Test
  @DisplayName("redirects with error when existing tenant is disabled")
  void onAuthenticationSuccess_redirectsWithError_whenTenantDisabled() throws Exception {

    final var principal =
        new DefaultSaml2AuthenticatedPrincipal(
            "user-name-id", Map.of("email", List.of("alice@corp.example.com")));

    when(authentication.getPrincipal()).thenReturn(principal);

    final Tenant existing = new Tenant();
    existing.setId(UUID.randomUUID());
    existing.setUsername("alice");
    existing.setEmail("alice@corp.example.com");
    existing.setEnabled(false);

    when(tenantRepository.findByUsernameOrEmail("alice@corp.example.com"))
        .thenReturn(Optional.of(existing));

    handler.onAuthenticationSuccess(request, response, authentication);

    final ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).sendRedirect(redirectCaptor.capture());

    assertThat(redirectCaptor.getValue())
        .isEqualTo("http://localhost:4200/login?error=userDisabled");
    verify(jwtUtils, never()).generateToken(any());
    verify(jwtUtils, never()).generateRefreshToken(any());
  }

  @Test
  @DisplayName("generates fallback email when SAML assertion has no email attribute")
  void onAuthenticationSuccess_generatesFallbackEmail_whenNoEmailAttribute() throws Exception {

    final var principal = new DefaultSaml2AuthenticatedPrincipal("plain-name-id", Map.of());

    when(authentication.getPrincipal()).thenReturn(principal);

    final Tenant saved = new Tenant();
    saved.setId(UUID.randomUUID());
    saved.setUsername("plain-name-id");
    saved.setEmail("plain-name-id@saml.originhub-os.com");

    when(tenantRepository.findByUsernameOrEmail(anyString())).thenReturn(Optional.empty());
    when(tenantRepository.save(any(Tenant.class))).thenReturn(saved);
    when(jwtUtils.generateToken(any(Tenant.class))).thenReturn("token");
    when(jwtUtils.generateRefreshToken(any(Tenant.class))).thenReturn("refresh");

    handler.onAuthenticationSuccess(request, response, authentication);

    final ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
    verify(tenantRepository).save(tenantCaptor.capture());
    assertThat(tenantCaptor.getValue().getEmail()).contains("saml.originhub-os.com");
  }

  @Test
  @DisplayName("uses email local-part as username when no username attribute configured")
  void onAuthenticationSuccess_derivesUsernameFromEmail() throws Exception {

    final var principal =
        new DefaultSaml2AuthenticatedPrincipal(
            "uid-123", Map.of("email", List.of("john.doe@corp.example.com")));

    when(authentication.getPrincipal()).thenReturn(principal);

    final Tenant saved = new Tenant();
    saved.setId(UUID.randomUUID());
    saved.setUsername("john.doe");
    saved.setEmail("john.doe@corp.example.com");

    when(tenantRepository.findByUsernameOrEmail("john.doe@corp.example.com"))
        .thenReturn(Optional.empty());
    when(tenantRepository.save(any(Tenant.class))).thenReturn(saved);
    when(jwtUtils.generateToken(any())).thenReturn("token");
    when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh");

    handler.onAuthenticationSuccess(request, response, authentication);

    final ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
    verify(tenantRepository).save(tenantCaptor.capture());
    assertThat(tenantCaptor.getValue().getUsername()).isEqualTo("john.doe");
  }

  @Test
  @DisplayName("uses NameID as email when it contains @ sign")
  void onAuthenticationSuccess_usesNameIdAsEmail_whenItLooksLikeEmail() throws Exception {

    final var principal =
        new DefaultSaml2AuthenticatedPrincipal("alice@saml-idp.example.com", Map.of());

    when(authentication.getPrincipal()).thenReturn(principal);

    final Tenant saved = new Tenant();
    saved.setId(UUID.randomUUID());
    saved.setUsername("alice");
    saved.setEmail("alice@saml-idp.example.com");

    when(tenantRepository.findByUsernameOrEmail("alice@saml-idp.example.com"))
        .thenReturn(Optional.empty());
    when(tenantRepository.save(any(Tenant.class))).thenReturn(saved);
    when(jwtUtils.generateToken(any())).thenReturn("token");
    when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh");

    handler.onAuthenticationSuccess(request, response, authentication);

    final ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
    verify(tenantRepository).save(tenantCaptor.capture());
    assertThat(tenantCaptor.getValue().getEmail()).isEqualTo("alice@saml-idp.example.com");
  }

  @Test
  @DisplayName("saved Account has SAML type and correct subjectId")
  void onAuthenticationSuccess_savesAccountWithSamlType() throws Exception {

    final var principal =
        new DefaultSaml2AuthenticatedPrincipal(
            "uid-abc", Map.of("email", List.of("saml-user@corp.com")));

    when(authentication.getPrincipal()).thenReturn(principal);

    final Tenant saved = new Tenant();
    saved.setId(UUID.randomUUID());
    saved.setUsername("saml-user");
    saved.setEmail("saml-user@corp.com");

    when(tenantRepository.findByUsernameOrEmail("saml-user@corp.com")).thenReturn(Optional.empty());
    when(tenantRepository.save(any(Tenant.class))).thenReturn(saved);
    when(jwtUtils.generateToken(any())).thenReturn("token");
    when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh");

    handler.onAuthenticationSuccess(request, response, authentication);

    final ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(accountCaptor.capture());
    assertThat(accountCaptor.getValue().getAccountType()).isEqualTo("SAML");
    assertThat(accountCaptor.getValue().getSubjectId()).isEqualTo("uid-abc");
  }
}
