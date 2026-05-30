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
package com.nuricanozturk.originhub.auth.controllers;

import com.nuricanozturk.originhub.auth.dtos.LoginForm;
import com.nuricanozturk.originhub.auth.dtos.RecoverPasswordForm;
import com.nuricanozturk.originhub.auth.dtos.RecoveryCodeRequestForm;
import com.nuricanozturk.originhub.auth.dtos.RefreshTokenForm;
import com.nuricanozturk.originhub.auth.dtos.RegistrationForm;
import com.nuricanozturk.originhub.auth.services.AuthService;
import com.nuricanozturk.originhub.shared.auth.dtos.LoginInfo;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

  private final @NonNull AuthService authService;
  private final @NonNull JwtUtils jwtUtils;

  @PostMapping("/login")
  public @NonNull ResponseEntity<LoginInfo> login(
      @RequestBody @Valid final @NonNull LoginForm form) {

    final var loginInfo = this.authService.login(form);

    return ResponseEntity.ok(loginInfo);
  }

  @PostMapping("/register")
  public @NonNull ResponseEntity<LoginInfo> register(
      @RequestBody @Valid final @NonNull RegistrationForm form) {

    final var loginInfo = this.authService.register(form);

    log.warn("{} has been registered!", form.getUsername());

    return ResponseEntity.ok(loginInfo);
  }

  @PostMapping("/recover-password")
  public @NonNull ResponseEntity<Void> recoverPassword(
      @RequestBody @Valid final @NonNull RecoverPasswordForm form) {

    this.authService.recoverPassword(form);

    return ResponseEntity.ok().build();
  }

  @PostMapping("/refresh-token")
  public @NonNull ResponseEntity<LoginInfo> refreshToken(
      @RequestBody @Valid final @NonNull RefreshTokenForm form) {

    this.jwtUtils.verifyAndValidate(form.getRefreshToken());

    final var tenantId = this.jwtUtils.extractUserId(form.getRefreshToken());

    final var loginInfo = this.authService.getTenantById(tenantId);

    return ResponseEntity.ok(loginInfo);
  }

  @PostMapping("/send-password-recovery-mail")
  public @NonNull ResponseEntity<Boolean> sendPasswordRecoveryMail(
      @RequestBody @Valid final @NonNull RecoveryCodeRequestForm form) {

    final var response = this.authService.getPasswordRecoveryCode(form);

    if (!response) {
      return ResponseEntity.internalServerError().body(false);
    }

    return ResponseEntity.ok(true);
  }
}
