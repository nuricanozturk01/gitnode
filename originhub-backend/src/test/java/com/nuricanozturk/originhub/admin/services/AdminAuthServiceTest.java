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
package com.nuricanozturk.originhub.admin.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.admin.dtos.AdminLoginForm;
import com.nuricanozturk.originhub.auth.api.AuthLoginPort;
import com.nuricanozturk.originhub.shared.auth.dtos.LoginInfo;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuthService unit tests")
class AdminAuthServiceTest {

  @Mock private AuthLoginPort authLoginPort;
  @Mock private PlatformAdminService platformAdminService;

  @InjectMocks private AdminAuthService adminAuthService;

  @Test
  @DisplayName("login returns AdminLoginInfo with platformAdmin true for admin user")
  void login_returnsAdminLoginInfo_whenPlatformAdmin() {

    final var form = AdminLoginForm.builder().usernameOrEmail("admin").password("Admin123").build();
    when(authLoginPort.login("admin", "Admin123"))
        .thenReturn(
            LoginInfo.builder()
                .username("admin")
                .email("admin@originhub.local")
                .token("access")
                .refreshToken("refresh")
                .expiresIn(3600)
                .refreshExpiresIn(86400)
                .build());
    when(platformAdminService.isPlatformAdmin("admin")).thenReturn(true);

    final var result = adminAuthService.login(form);

    assertThat(result.isPlatformAdmin()).isTrue();
    assertThat(result.getToken()).isEqualTo("access");
    assertThat(result.getUsername()).isEqualTo("admin");
  }

  @Test
  @DisplayName("login throws when authenticated user is not platform admin")
  void login_throws_whenNotPlatformAdmin() {

    final var form = AdminLoginForm.builder().usernameOrEmail("alice").password("Alice123").build();
    when(authLoginPort.login("alice", "Alice123"))
        .thenReturn(LoginInfo.builder().username("alice").email("alice@example.com").build());
    when(platformAdminService.isPlatformAdmin("alice")).thenReturn(false);

    assertThatThrownBy(() -> adminAuthService.login(form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("platformAdminRequired");
  }
}
