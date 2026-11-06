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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.auth.dtos.LoginForm;
import dev.gitnode.os.auth.dtos.RecoverPasswordForm;
import dev.gitnode.os.auth.dtos.RecoveryCodeRequestForm;
import dev.gitnode.os.auth.dtos.RegistrationForm;
import dev.gitnode.os.identity.api.TenantAccountStore;
import dev.gitnode.os.identity.dtos.LoginInfo;
import dev.gitnode.os.identity.entities.Tenant;
import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService unit tests")
class AuthServiceTest {

  private static final String VALID_PASSWORD = "Pass1word";

  @Mock private TenantAccountStore tenantRepository;
  @Mock private JwtUtils jwtUtils;
  @Mock private PasswordRecoveryNotifier recoveryNotifier;
  @Mock private dev.gitnode.os.auth.services.RefreshTokenService refreshTokens;

  @org.mockito.Spy
  private dev.gitnode.os.identity.services.PasswordHasher passwordHasher =
      new dev.gitnode.os.identity.services.PasswordHasher();

  @InjectMocks private AuthService authService;

  @BeforeEach
  void provideNewAccounts() {
    lenient().when(tenantRepository.newAccount()).thenAnswer(inv -> new Tenant());
  }

  @Test
  @DisplayName("register throws BadRequestException when username already exists")
  void register_throws_whenUsernameInUse() {
    RegistrationForm form =
        RegistrationForm.builder()
            .username("alice")
            .email("alice@example.com")
            .password(VALID_PASSWORD)
            .build();
    when(tenantRepository.existsByUsername("alice")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(form))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("usernameInUse");

    verify(tenantRepository, never()).save(any());
  }

  @Test
  @DisplayName("register throws BadRequestException when email already exists")
  void register_throws_whenEmailInUse() {
    RegistrationForm form =
        RegistrationForm.builder()
            .username("alice")
            .email("alice@example.com")
            .password(VALID_PASSWORD)
            .build();
    when(tenantRepository.existsByUsername("alice")).thenReturn(false);
    when(tenantRepository.existsByEmail("alice@example.com")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(form))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("emailInUse");

    verify(tenantRepository, never()).save(any());
  }

  @Test
  @DisplayName("register saves tenant with lowercased username and email and returns LoginInfo")
  void register_returnsLoginInfo_whenSuccess() {
    RegistrationForm form =
        RegistrationForm.builder()
            .username("Alice")
            .email("Alice@Example.COM")
            .password(VALID_PASSWORD)
            .build();
    when(tenantRepository.existsByUsername("alice")).thenReturn(false);
    when(tenantRepository.existsByEmail("alice@example.com")).thenReturn(false);
    when(tenantRepository.save(any(Tenant.class)))
        .thenAnswer(
            invocation -> {
              Tenant saved = invocation.getArgument(0);
              saved.setId(UUID.randomUUID());
              return saved;
            });
    when(jwtUtils.generateToken(any(Tenant.class))).thenReturn("access-token");
    when(refreshTokens.issue(any(Tenant.class))).thenReturn("refresh-token");

    LoginInfo result = authService.register(form);

    assertThat(result.getToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    assertThat(result.getUsername()).isEqualTo("alice");
    assertThat(result.getEmail()).isEqualTo("alice@example.com");
    assertThat(result.getExpiresIn()).isEqualTo(JwtUtils.ACCESS_EXPIRATION_SECONDS);
    assertThat(result.getRefreshExpiresIn()).isEqualTo(JwtUtils.REFRESH_EXPIRATION_SECONDS);

    ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
    verify(tenantRepository).save(tenantCaptor.capture());
    Tenant saved = tenantCaptor.getValue();
    assertThat(saved.getUsername()).isEqualTo("alice");
    assertThat(saved.getEmail()).isEqualTo("alice@example.com");
    assertThat(saved.getSalt()).isNull();
    assertThat(saved.getHash()).startsWith("$2");
    assertThat(passwordHasher.matches(VALID_PASSWORD, saved)).isTrue();
  }

  @Test
  @DisplayName("login throws AccessNotAllowedException when user does not exist")
  void login_throws_whenUserNotFound() {
    LoginForm form =
        LoginForm.builder().usernameOrEmail("missing@example.com").password(VALID_PASSWORD).build();
    when(tenantRepository.findByUsernameOrEmail("missing@example.com"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("wrongPassword");
  }

  @Test
  @DisplayName("login throws AccessNotAllowedException when password is wrong")
  void login_throws_whenWrongPassword() {
    String salt = "fixedsalt1234567";
    Tenant tenant = tenantWithCredentials("alice", "alice@example.com", "OtherPass1", salt);
    LoginForm form = LoginForm.builder().usernameOrEmail("alice").password(VALID_PASSWORD).build();
    when(tenantRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(tenant));

    assertThatThrownBy(() -> authService.login(form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("wrongPassword");
  }

  @Test
  @DisplayName("login throws AccessNotAllowedException when user is disabled")
  void login_throws_whenUserDisabled() {
    String salt = "fixedsalt1234567";
    Tenant tenant = tenantWithCredentials("alice", "alice@example.com", VALID_PASSWORD, salt);
    tenant.setEnabled(false);
    LoginForm form = LoginForm.builder().usernameOrEmail("alice").password(VALID_PASSWORD).build();
    when(tenantRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(tenant));

    assertThatThrownBy(() -> authService.login(form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("userDisabled");
  }

  @Test
  @DisplayName("login returns LoginInfo when credentials are valid")
  void login_returnsLoginInfo_whenSuccess() {
    String salt = "fixedsalt1234567";
    Tenant tenant = tenantWithCredentials("alice", "alice@example.com", VALID_PASSWORD, salt);
    LoginForm form = LoginForm.builder().usernameOrEmail("Alice").password(VALID_PASSWORD).build();
    when(tenantRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(tenant));
    when(jwtUtils.generateToken(tenant)).thenReturn("access-token");
    when(refreshTokens.issue(tenant)).thenReturn("refresh-token");

    LoginInfo result = authService.login(form);

    assertThat(result.getToken()).isEqualTo("access-token");
    assertThat(result.getUsername()).isEqualTo("alice");
    assertThat(result.getEmail()).isEqualTo("alice@example.com");
  }

  @Test
  @DisplayName("login upgrades a legacy SHA-256 hash to bcrypt and keeps the password working")
  void login_upgradesLegacyHash() {
    Tenant tenant =
        tenantWithCredentials("alice", "alice@example.com", VALID_PASSWORD, "fixedsalt1234567");
    LoginForm form = LoginForm.builder().usernameOrEmail("alice").password(VALID_PASSWORD).build();
    when(tenantRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(tenant));
    when(jwtUtils.generateToken(tenant)).thenReturn("access-token");
    when(refreshTokens.issue(tenant)).thenReturn("refresh-token");

    authService.login(form);

    assertThat(tenant.getHash()).startsWith("$2");
    assertThat(tenant.getSalt()).isNull();
    assertThat(passwordHasher.matches(VALID_PASSWORD, tenant)).isTrue();
    verify(tenantRepository).save(tenant);
  }

  @Test
  @DisplayName("login with a wrong password never rewrites the stored hash")
  void login_wrongPasswordKeepsHash() {
    Tenant tenant =
        tenantWithCredentials("alice", "alice@example.com", "OtherPass1", "fixedsalt1234567");
    String before = tenant.getHash();
    when(tenantRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(tenant));

    assertThatThrownBy(
            () ->
                authService.login(
                    LoginForm.builder().usernameOrEmail("alice").password(VALID_PASSWORD).build()))
        .isInstanceOf(AccessNotAllowedException.class);

    assertThat(tenant.getHash()).isEqualTo(before);
    verify(tenantRepository, never()).save(any(Tenant.class));
  }

  private static Tenant recoveryTenant(final boolean enabled) {
    Tenant tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setUsername("alice");
    tenant.setEnabled(enabled);
    return tenant;
  }

  @Test
  @DisplayName("requestPasswordRecovery does nothing observable for unknown accounts")
  void requestPasswordRecovery_ignoresUnknownAccount() {
    when(tenantRepository.findByUsernameOrEmail("nobody@example.com")).thenReturn(Optional.empty());

    authService.requestPasswordRecovery(
        RecoveryCodeRequestForm.builder().usernameOrEmail("nobody@example.com").build());

    verify(tenantRepository, never()).updatePasswordRecoveryCode(any(), anyString(), any());
    verify(recoveryNotifier, never()).send(any(), anyString());
  }

  @Test
  @DisplayName("requestPasswordRecovery stores only a hash with an expiry and mails the plain code")
  void requestPasswordRecovery_storesHashAndSendsCode() {
    Tenant tenant = recoveryTenant(true);
    when(tenantRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(tenant));

    authService.requestPasswordRecovery(
        RecoveryCodeRequestForm.builder().usernameOrEmail("alice").build());

    ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
    verify(recoveryNotifier).send(eq(tenant), sent.capture());
    ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Instant> expires = ArgumentCaptor.forClass(Instant.class);
    verify(tenantRepository)
        .updatePasswordRecoveryCode(eq(tenant.getId()), stored.capture(), expires.capture());
    assertThat(stored.getValue()).isEqualTo(DigestUtils.sha256Hex(sent.getValue()));
    assertThat(stored.getValue()).isNotEqualTo(sent.getValue());
    assertThat(expires.getValue())
        .isBetween(Instant.now().plusSeconds(29 * 60), Instant.now().plusSeconds(31 * 60));
  }

  @Test
  @DisplayName("requestPasswordRecovery ignores disabled accounts")
  void requestPasswordRecovery_ignoresDisabledAccount() {
    when(tenantRepository.findByUsernameOrEmail("alice"))
        .thenReturn(Optional.of(recoveryTenant(false)));

    authService.requestPasswordRecovery(
        RecoveryCodeRequestForm.builder().usernameOrEmail("alice").build());

    verify(recoveryNotifier, never()).send(any(), anyString());
  }

  @Test
  @DisplayName("recoverPassword throws AccessNotAllowedException when recovery code is invalid")
  void recoverPassword_throws_whenRecoveryCodeInvalid() {
    RecoverPasswordForm form =
        RecoverPasswordForm.builder()
            .recoveryCode("x".repeat(150))
            .password(VALID_PASSWORD)
            .build();
    when(tenantRepository.findByPasswordRecoveryCode(DigestUtils.sha256Hex(form.getRecoveryCode())))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.recoverPassword(form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("accessDenied");
  }

  @Test
  @DisplayName("recoverPassword rejects an expired recovery code")
  void recoverPassword_throws_whenCodeExpired() {
    Tenant tenant =
        tenantWithCredentials("alice", "alice@example.com", "OldPass1word", "oldsalt123456789");
    String recoveryCode = "d".repeat(150);
    tenant.setPasswordRecoveryCode(DigestUtils.sha256Hex(recoveryCode));
    tenant.setPasswordRecoveryExpiresAt(Instant.now().minusSeconds(1));
    when(tenantRepository.findByPasswordRecoveryCode(DigestUtils.sha256Hex(recoveryCode)))
        .thenReturn(Optional.of(tenant));

    assertThatThrownBy(
            () ->
                authService.recoverPassword(
                    RecoverPasswordForm.builder()
                        .recoveryCode(recoveryCode)
                        .password(VALID_PASSWORD)
                        .build()))
        .isInstanceOf(AccessNotAllowedException.class);
    verify(tenantRepository, never()).save(any(Tenant.class));
  }

  @Test
  @DisplayName("recoverPassword updates hash, clears recovery code, and saves tenant")
  void recoverPassword_updatesPasswordAndClearsCode_whenValid() {
    String oldSalt = "oldsalt123456789";
    Tenant tenant = tenantWithCredentials("alice", "alice@example.com", "OldPass1word", oldSalt);
    tenant.setPasswordRecoveryCode("valid-recovery-code");
    String recoveryCode = "c".repeat(150);
    tenant.setPasswordRecoveryCode(DigestUtils.sha256Hex(recoveryCode));
    tenant.setPasswordRecoveryExpiresAt(Instant.now().plusSeconds(600));
    RecoverPasswordForm form =
        RecoverPasswordForm.builder().recoveryCode(recoveryCode).password(VALID_PASSWORD).build();
    when(tenantRepository.findByPasswordRecoveryCode(DigestUtils.sha256Hex(recoveryCode)))
        .thenReturn(Optional.of(tenant));
    when(tenantRepository.save(tenant)).thenReturn(tenant);

    authService.recoverPassword(form);

    assertThat(tenant.getPasswordRecoveryCode()).isNull();
    assertThat(tenant.getSalt()).isNull();
    assertThat(passwordHasher.matches(VALID_PASSWORD, tenant)).isTrue();
    verify(tenantRepository).save(tenant);
  }

  @Test
  @DisplayName("refresh rejects tokens whose account no longer exists")
  void refresh_throws_whenTenantMissing() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
    when(refreshTokens.rotate(eq("old"), any()))
        .thenAnswer(
            invocation ->
                invocation.<RefreshTokenService.TenantLookup>getArgument(1).find(tenantId));

    assertThatThrownBy(() -> authService.refresh("old"))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("userNotExist");
  }

  @Test
  @DisplayName("refresh returns a rotated refresh token for an existing tenant")
  void refresh_returnsRotatedLoginInfo() {
    Tenant tenant =
        tenantWithCredentials("alice", "alice@example.com", VALID_PASSWORD, "salt123456789012");
    tenant.setId(UUID.randomUUID());
    when(refreshTokens.rotate(eq("old"), any()))
        .thenReturn(new RefreshTokenService.Rotation(tenant, "rotated"));
    when(jwtUtils.generateToken(tenant)).thenReturn("access-token");

    LoginInfo result = authService.refresh("old");

    assertThat(result.getToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("rotated");
    assertThat(result.getUsername()).isEqualTo("alice");
  }

  private static Tenant tenantWithCredentials(
      final String username, final String email, final String password, final String salt) {
    Tenant tenant = new Tenant();
    tenant.setUsername(username);
    tenant.setEmail(email);
    tenant.setSalt(salt);
    tenant.setHash(DigestUtils.sha256Hex(password + salt));
    return tenant;
  }
}
