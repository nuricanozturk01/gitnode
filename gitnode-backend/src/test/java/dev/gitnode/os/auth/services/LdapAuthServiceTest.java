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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.auth.dtos.LdapLoginForm;
import dev.gitnode.os.auth.entities.Account;
import dev.gitnode.os.auth.entities.Organization;
import dev.gitnode.os.auth.repositories.AccountRepository;
import dev.gitnode.os.auth.repositories.OrganizationRepository;
import dev.gitnode.os.shared.auth.dtos.LoginInfo;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.util.List;
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
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.NamingException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;

@ExtendWith(MockitoExtension.class)
@DisplayName("LdapAuthService unit tests")
class LdapAuthServiceTest {

  private static final String WORK_EMAIL = "alice@acme.com";

  @Mock private OrganizationRepository organizationRepository;
  @Mock private LdapConnectionService ldapConnectionService;
  @Mock private LdapTemplate ldapTemplate;
  @Mock private TenantRepository tenantRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private JwtUtils jwtUtils;

  @InjectMocks private LdapAuthService ldapAuthService;

  @BeforeEach
  void setUp() {

    final var organization = sampleLdapOrg();
    lenient()
        .when(organizationRepository.findLdapEnabledByEmailDomain("acme.com"))
        .thenReturn(Optional.of(organization));
    lenient().when(ldapConnectionService.createTemplate(organization)).thenReturn(ldapTemplate);
  }

  @Test
  @DisplayName("authenticate throws AccessNotAllowedException when LDAP throws NamingException")
  void authenticate_throws_whenLdapThrowsNamingException() {

    final LdapLoginForm form =
        LdapLoginForm.builder().email(WORK_EMAIL).username("alice").password("wrong").build();

    doThrow(
            new AuthenticationException(
                new javax.naming.AuthenticationException("Bad credentials")))
        .when(ldapTemplate)
        .authenticate(any(LdapQuery.class), anyString());

    assertThatThrownBy(() -> ldapAuthService.authenticate(form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("wrongPassword");

    verify(tenantRepository, never()).save(any());
  }

  @Test
  @DisplayName("authenticate throws on generic NamingException (connection error)")
  void authenticate_throws_whenNamingExceptionOccurs() {

    final LdapLoginForm form =
        LdapLoginForm.builder().email(WORK_EMAIL).username("alice").password("pass").build();

    doThrow(
            new NamingException("connection refused") {
              private static final long serialVersionUID = 1L;
            })
        .when(ldapTemplate)
        .authenticate(any(LdapQuery.class), anyString());

    assertThatThrownBy(() -> ldapAuthService.authenticate(form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("wrongPassword");
  }

  @Test
  @DisplayName("authenticate throws BadRequestException when user search base does not exist")
  void authenticate_throws_whenUserSearchBaseNotFound() {

    final LdapLoginForm form =
        LdapLoginForm.builder().email(WORK_EMAIL).username("alice").password("pass").build();

    doThrow(
            new NamingException("[LDAP: error code 32 - No Such Object]") {
              private static final long serialVersionUID = 1L;
            })
        .when(ldapTemplate)
        .authenticate(any(LdapQuery.class), anyString());

    assertThatThrownBy(() -> ldapAuthService.authenticate(form))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("ldapUserSearchBaseInvalid");

    verify(tenantRepository, never()).save(any());
  }

  @Test
  @DisplayName("authenticate throws when existing tenant is disabled")
  void authenticate_throws_whenTenantDisabled() {

    final LdapLoginForm form =
        LdapLoginForm.builder().email(WORK_EMAIL).username("alice").password("correct").build();

    final Tenant existing = new Tenant();
    existing.setId(UUID.randomUUID());
    existing.setUsername("alice");
    existing.setEmail("alice@ldap.gitnode-os.com");
    existing.setEnabled(false);

    when(ldapTemplate.search(any(LdapQuery.class), any(AttributesMapper.class)))
        .thenReturn(List.of());
    when(tenantRepository.findByUsernameOrEmail("alice@ldap.gitnode-os.com"))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> ldapAuthService.authenticate(form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("userDisabled");

    verify(jwtUtils, never()).generateToken(any());
  }

  @Test
  @DisplayName("authenticate returns LoginInfo for existing tenant on successful bind")
  void authenticate_returnsLoginInfo_forExistingTenant() {

    final LdapLoginForm form =
        LdapLoginForm.builder().email(WORK_EMAIL).username("alice").password("correct").build();

    final Tenant existing = new Tenant();
    existing.setId(UUID.randomUUID());
    existing.setUsername("alice");
    existing.setEmail("alice@ldap.gitnode-os.com");

    when(ldapTemplate.search(any(LdapQuery.class), any(AttributesMapper.class)))
        .thenReturn(List.of());
    when(tenantRepository.findByUsernameOrEmail("alice@ldap.gitnode-os.com"))
        .thenReturn(Optional.of(existing));
    when(jwtUtils.generateToken(existing)).thenReturn("access-token");
    when(jwtUtils.generateRefreshToken(existing)).thenReturn("refresh-token");

    final LoginInfo result = ldapAuthService.authenticate(form);

    assertThat(result.getToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    assertThat(result.getUsername()).isEqualTo("alice");
  }

  @Test
  @DisplayName("authenticate provisions new tenant when LDAP user has no local account")
  void authenticate_createsNewTenant_whenNoLocalAccount() {

    final LdapLoginForm form =
        LdapLoginForm.builder()
            .email("newuser@acme.com")
            .username("newuser")
            .password("secret")
            .build();

    final Tenant saved = new Tenant();
    saved.setId(UUID.randomUUID());
    saved.setUsername("newuser");
    saved.setEmail("newuser@ldap.gitnode-os.com");

    when(ldapTemplate.search(any(LdapQuery.class), any(AttributesMapper.class)))
        .thenReturn(List.of());
    when(tenantRepository.findByUsernameOrEmail(anyString())).thenReturn(Optional.empty());
    when(tenantRepository.save(any(Tenant.class))).thenReturn(saved);
    when(jwtUtils.generateToken(saved)).thenReturn("access-token");
    when(jwtUtils.generateRefreshToken(saved)).thenReturn("refresh-token");

    final LoginInfo result = ldapAuthService.authenticate(form);

    assertThat(result.getToken()).isEqualTo("access-token");
    assertThat(result.getUsername()).isEqualTo("newuser");

    final ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(accountCaptor.capture());
    assertThat(accountCaptor.getValue().getAccountType()).isEqualTo("LDAP");
    assertThat(accountCaptor.getValue().getSubjectId()).isEqualTo("newuser");
  }

  @Test
  @DisplayName("authenticate lowercases username before LDAP search")
  void authenticate_lowercasesUsername() {

    final LdapLoginForm form =
        LdapLoginForm.builder().email(WORK_EMAIL).username("TESTUSER").password("pass").build();

    doThrow(
            new AuthenticationException(
                new javax.naming.AuthenticationException("Bad credentials")))
        .when(ldapTemplate)
        .authenticate(any(LdapQuery.class), anyString());

    assertThatThrownBy(() -> ldapAuthService.authenticate(form))
        .isInstanceOf(AccessNotAllowedException.class);

    final ArgumentCaptor<LdapQuery> queryCaptor = ArgumentCaptor.forClass(LdapQuery.class);
    verify(ldapTemplate).authenticate(queryCaptor.capture(), anyString());
    assertThat(queryCaptor.getValue().filter().encode()).contains("testuser");
  }

  @Test
  @DisplayName("authenticate returns correct token expiry values")
  void authenticate_returnsCorrectExpiryValues() {

    final LdapLoginForm form =
        LdapLoginForm.builder().email(WORK_EMAIL).username("alice").password("pass").build();

    final Tenant tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setUsername("alice");
    tenant.setEmail("alice@ldap.gitnode-os.com");

    when(ldapTemplate.search(any(LdapQuery.class), any(AttributesMapper.class)))
        .thenReturn(List.of());
    when(tenantRepository.findByUsernameOrEmail(anyString())).thenReturn(Optional.of(tenant));
    when(jwtUtils.generateToken(tenant)).thenReturn("access-token");
    when(jwtUtils.generateRefreshToken(tenant)).thenReturn("refresh-token");

    final LoginInfo result = ldapAuthService.authenticate(form);

    assertThat(result.getExpiresIn()).isEqualTo(JwtUtils.ACCESS_EXPIRATION_SECONDS);
    assertThat(result.getRefreshExpiresIn()).isEqualTo(JwtUtils.REFRESH_EXPIRATION_SECONDS);
  }

  @Test
  @DisplayName("authenticate stores LDAP groups on account upsert")
  void authenticate_storesLdapGroupsOnAccount() {

    final LdapLoginForm form =
        LdapLoginForm.builder().email("bob@acme.com").username("bob").password("pass").build();

    final Tenant tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setUsername("bob");
    tenant.setEmail("bob@ldap.gitnode-os.com");

    when(ldapTemplate.search(any(LdapQuery.class), any(AttributesMapper.class)))
        .thenReturn(List.of())
        .thenReturn(List.of("developers", "qa"));
    when(tenantRepository.findByUsernameOrEmail(anyString())).thenReturn(Optional.empty());
    when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
    when(jwtUtils.generateToken(tenant)).thenReturn("tok");
    when(jwtUtils.generateRefreshToken(tenant)).thenReturn("ref");

    ldapAuthService.authenticate(form);

    final ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(captor.capture());
    assertThat(captor.getValue().getLdapGroups()).isEqualTo("developers,qa");
  }

  @Test
  @DisplayName("authenticate updates ldap_groups on existing account")
  void authenticate_updatesGroupsOnExistingAccount() {

    final LdapLoginForm form =
        LdapLoginForm.builder().email(WORK_EMAIL).username("alice").password("pass").build();

    final Tenant tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setUsername("alice");
    tenant.setEmail("alice@ldap.gitnode-os.com");

    final Account existingAccount = new Account();
    existingAccount.setAccountType("LDAP");
    existingAccount.setSubjectId("alice");
    existingAccount.setLdapGroups("oldgroup");

    when(ldapTemplate.search(any(LdapQuery.class), any(AttributesMapper.class)))
        .thenReturn(List.of())
        .thenReturn(List.of("admins"));
    when(tenantRepository.findByUsernameOrEmail(anyString())).thenReturn(Optional.of(tenant));
    when(accountRepository.findByAccountTypeAndSubjectId(eq("LDAP"), eq("alice")))
        .thenReturn(Optional.of(existingAccount));
    when(jwtUtils.generateToken(tenant)).thenReturn("tok");
    when(jwtUtils.generateRefreshToken(tenant)).thenReturn("ref");

    ldapAuthService.authenticate(form);

    final ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(captor.capture());
    assertThat(captor.getValue().getLdapGroups()).isEqualTo("admins");
  }

  private static Organization sampleLdapOrg() {

    final var org = new Organization();
    org.setSlug("acme");
    org.setName("Acme");
    org.setLdapEnabled(true);
    org.setLdapUrl("ldap://localhost:389");
    org.setLdapBaseDn("dc=acme,dc=com");
    org.setLdapUserSearchBase("ou=people");
    org.setLdapUserSearchFilter("(uid={0})");
    org.setLdapEmailAttribute("mail");
    org.setLdapDisplayNameAttribute("cn");
    org.setLdapGroupSearchBase("ou=groups");
    org.setLdapGroupSearchFilter("(memberUid={0})");
    org.setLdapGroupRoleAttribute("cn");
    org.setEmailDomains(List.of("acme.com"));
    return org;
  }
}
