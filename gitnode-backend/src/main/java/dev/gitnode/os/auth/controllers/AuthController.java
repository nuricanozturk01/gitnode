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
package dev.gitnode.os.auth.controllers;

import dev.gitnode.os.auth.dtos.LoginForm;
import dev.gitnode.os.auth.dtos.RecoverPasswordForm;
import dev.gitnode.os.auth.dtos.RecoveryCodeRequestForm;
import dev.gitnode.os.auth.dtos.RefreshTokenForm;
import dev.gitnode.os.auth.dtos.RegistrationForm;
import dev.gitnode.os.auth.services.AuthService;
import dev.gitnode.os.shared.auth.dtos.LoginInfo;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@NullMarked
@RequiredArgsConstructor
class AuthController {

  private final AuthService authService;
  private final JwtUtils jwtUtils;

  @PostMapping("/login")
  @RateLimit(limit = 30, windowSeconds = 60, key = "auth.login")
  public ResponseEntity<LoginInfo> login(@RequestBody @Valid final LoginForm form) {

    final var loginInfo = this.authService.login(form);

    return ResponseEntity.ok(loginInfo);
  }

  @PostMapping("/register")
  @RateLimit(limit = 20, windowSeconds = 300, key = "auth.register")
  public ResponseEntity<LoginInfo> register(@RequestBody @Valid final RegistrationForm form) {

    final var loginInfo = this.authService.register(form);

    log.warn("{} has been registered!", form.getUsername());

    return ResponseEntity.ok(loginInfo);
  }

  @PostMapping("/recover-password")
  @RateLimit(limit = 20, windowSeconds = 300, key = "auth.recover-password")
  public ResponseEntity<Void> recoverPassword(@RequestBody @Valid final RecoverPasswordForm form) {

    this.authService.recoverPassword(form);

    return ResponseEntity.ok().build();
  }

  @PostMapping("/refresh-token")
  @RateLimit(limit = 60, windowSeconds = 60, key = "auth.refresh-token")
  public ResponseEntity<LoginInfo> refreshToken(@RequestBody @Valid final RefreshTokenForm form) {

    this.jwtUtils.verifyAndValidate(form.getRefreshToken());

    final var tenantId = this.jwtUtils.extractUserId(form.getRefreshToken());

    final var loginInfo = this.authService.getTenantById(tenantId);

    return ResponseEntity.ok(loginInfo);
  }

  @PostMapping("/send-password-recovery-mail")
  @RateLimit(limit = 10, windowSeconds = 300, key = "auth.send-recovery-mail")
  public ResponseEntity<Boolean> sendPasswordRecoveryMail(
      @RequestBody @Valid final RecoveryCodeRequestForm form) {

    final var response = this.authService.getPasswordRecoveryCode(form);

    if (!response) {
      return ResponseEntity.internalServerError().body(false);
    }

    return ResponseEntity.ok(true);
  }
}
