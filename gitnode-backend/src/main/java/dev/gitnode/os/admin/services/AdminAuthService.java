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
package dev.gitnode.os.admin.services;

import dev.gitnode.os.admin.dtos.AdminLoginForm;
import dev.gitnode.os.admin.dtos.AdminLoginInfo;
import dev.gitnode.os.auth.api.AuthLoginPort;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NullMarked
public class AdminAuthService {

  private final AuthLoginPort authLoginPort;
  private final PlatformAdminService platformAdminService;

  public AdminLoginInfo login(final AdminLoginForm form) {

    final var loginInfo =
        this.authLoginPort.login(
            form.getUsernameOrEmail().toLowerCase(Locale.getDefault()), form.getPassword());

    if (!this.platformAdminService.isPlatformAdmin(loginInfo.getUsername())) {
      throw new AccessNotAllowedException("platformAdminRequired");
    }

    return AdminLoginInfo.builder()
        .token(loginInfo.getToken())
        .refreshToken(loginInfo.getRefreshToken())
        .email(loginInfo.getEmail())
        .username(loginInfo.getUsername())
        .expiresIn(loginInfo.getExpiresIn())
        .refreshExpiresIn(loginInfo.getRefreshExpiresIn())
        .platformAdmin(true)
        .build();
  }
}
