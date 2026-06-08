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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.auth.dtos.LoginForm;
import dev.gitnode.os.auth.dtos.RecoverPasswordForm;
import dev.gitnode.os.auth.dtos.RecoveryCodeRequestForm;
import dev.gitnode.os.auth.dtos.RegistrationForm;
import dev.gitnode.os.shared.auth.dtos.LoginInfo;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
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

  @Mock private TenantRepository tenantRepository;
  @Mock private JwtUtils jwtUtils;

  @InjectMocks private AuthService authService;

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
    when(jwtUtils.generateRefreshToken(any(Tenant.class))).thenReturn("refresh-token");

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
    assertThat(saved.getSalt()).isNotBlank();
    assertThat(saved.getHash()).isEqualTo(DigestUtils.sha256Hex(VALID_PASSWORD + saved.getSalt()));
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
        .hasMessageContaining("userNotExist");
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
    when(jwtUtils.generateRefreshToken(tenant)).thenReturn("refresh-token");

    LoginInfo result = authService.login(form);

    assertThat(result.getToken()).isEqualTo("access-token");
    assertThat(result.getUsername()).isEqualTo("alice");
    assertThat(result.getEmail()).isEqualTo("alice@example.com");
  }

  @Test
  @DisplayName("getPasswordRecoveryCode returns false when user is unknown")
  void getPasswordRecoveryCode_returnsFalse_whenUserNotFound() {
    RecoveryCodeRequestForm form =
        RecoveryCodeRequestForm.builder().usernameOrEmail("nobody@example.com").build();
    when(tenantRepository.findByUsernameOrEmail("nobody@example.com")).thenReturn(Optional.empty());

    boolean result = authService.getPasswordRecoveryCode(form);

    assertThat(result).isFalse();
    verify(tenantRepository, never()).updatePasswordRecoveryCode(any(), anyString());
  }

  @Test
  @DisplayName("getPasswordRecoveryCode generates code when user exists and has no pending code")
  void getPasswordRecoveryCode_updatesCode_whenNoPendingCode() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setUsername("alice");
    tenant.setPasswordRecoveryCode(null);
    RecoveryCodeRequestForm form =
        RecoveryCodeRequestForm.builder().usernameOrEmail("alice").build();
    when(tenantRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(tenant));

    boolean result = authService.getPasswordRecoveryCode(form);

    assertThat(result).isTrue();
    verify(tenantRepository).updatePasswordRecoveryCode(eq(tenantId), anyString());
  }

  @Test
  @DisplayName("getPasswordRecoveryCode skips update when recovery code already exists")
  void getPasswordRecoveryCode_skipsUpdate_whenCodeAlreadySet() {
    Tenant tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setUsername("alice");
    tenant.setPasswordRecoveryCode("existing-code");
    RecoveryCodeRequestForm form =
        RecoveryCodeRequestForm.builder().usernameOrEmail("alice").build();
    when(tenantRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(tenant));

    boolean result = authService.getPasswordRecoveryCode(form);

    assertThat(result).isTrue();
    verify(tenantRepository, never()).updatePasswordRecoveryCode(any(), anyString());
  }

  @Test
  @DisplayName("recoverPassword throws AccessNotAllowedException when recovery code is invalid")
  void recoverPassword_throws_whenRecoveryCodeInvalid() {
    RecoverPasswordForm form =
        RecoverPasswordForm.builder()
            .recoveryCode("x".repeat(150))
            .password(VALID_PASSWORD)
            .build();
    when(tenantRepository.findByPasswordRecoveryCode(form.getRecoveryCode()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.recoverPassword(form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("accessDenied");
  }

  @Test
  @DisplayName("recoverPassword updates hash, clears recovery code, and saves tenant")
  void recoverPassword_updatesPasswordAndClearsCode_whenValid() {
    String oldSalt = "oldsalt123456789";
    Tenant tenant = tenantWithCredentials("alice", "alice@example.com", "OldPass1word", oldSalt);
    tenant.setPasswordRecoveryCode("valid-recovery-code");
    String recoveryCode = "c".repeat(150);
    tenant.setPasswordRecoveryCode(recoveryCode);
    RecoverPasswordForm form =
        RecoverPasswordForm.builder().recoveryCode(recoveryCode).password(VALID_PASSWORD).build();
    when(tenantRepository.findByPasswordRecoveryCode(recoveryCode)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(tenant)).thenReturn(tenant);

    authService.recoverPassword(form);

    assertThat(tenant.getPasswordRecoveryCode()).isNull();
    assertThat(tenant.getSalt()).isNotEqualTo(oldSalt);
    assertThat(tenant.getHash())
        .isEqualTo(DigestUtils.sha256Hex(VALID_PASSWORD + tenant.getSalt()));
    verify(tenantRepository).save(tenant);
  }

  @Test
  @DisplayName("getTenantById throws AccessNotAllowedException when tenant not found")
  void getTenantById_throws_whenTenantMissing() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.getTenantById(tenantId))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("userNotExist");
  }

  @Test
  @DisplayName("getTenantById returns LoginInfo for existing tenant")
  void getTenantById_returnsLoginInfo_whenTenantExists() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant =
        tenantWithCredentials("alice", "alice@example.com", VALID_PASSWORD, "salt123456789012");
    tenant.setId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(jwtUtils.generateToken(tenant)).thenReturn("access-token");
    when(jwtUtils.generateRefreshToken(tenant)).thenReturn("refresh-token");

    LoginInfo result = authService.getTenantById(tenantId);

    assertThat(result.getToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
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
