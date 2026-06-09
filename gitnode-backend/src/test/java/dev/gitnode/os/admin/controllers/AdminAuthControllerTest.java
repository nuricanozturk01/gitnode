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
package dev.gitnode.os.admin.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.gitnode.os.admin.dtos.AdminLoginForm;
import dev.gitnode.os.admin.dtos.AdminLoginInfo;
import dev.gitnode.os.admin.services.AdminAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuthController unit tests")
class AdminAuthControllerTest {

  @Mock private AdminAuthService adminAuthService;

  @InjectMocks private AdminAuthController adminAuthController;

  @Test
  @DisplayName("POST /api/admin/auth/login returns AdminLoginInfo")
  void login_returnsAdminLoginInfo() {

    when(adminAuthService.login(any(AdminLoginForm.class)))
        .thenReturn(
            AdminLoginInfo.builder()
                .username("admin")
                .email("admin@gitnode.local")
                .token("access")
                .refreshToken("refresh")
                .expiresIn(3600)
                .refreshExpiresIn(86400)
                .platformAdmin(true)
                .build());

    final var response =
        adminAuthController.login(
            AdminLoginForm.builder().usernameOrEmail("admin").password("Admin123").build());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isPlatformAdmin()).isTrue();
    assertThat(response.getBody().getToken()).isEqualTo("access");
  }
}
